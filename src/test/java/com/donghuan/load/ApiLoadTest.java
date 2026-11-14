package com.donghuan.load;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 接口压测用例（对已启动的应用发压，不启动 Spring 上下文）。
 *
 * 用法：
 *   1. 先启动 DongHuanApplication（默认 8080），并确保 MySQL / EMQX / Modbus 模拟器在线；
 *   2. 运行本用例：mvn test -Dtest=ApiLoadTest
 *
 * 可调参数（-D 传入，IDEA 里放 VM options）：
 *   load.baseUrl          默认 http://localhost:8080
 *   load.users            并发会话数，默认 20
 *   load.warmupSeconds    预热秒数，默认 3（不计入统计）
 *   load.durationSeconds  压测秒数，默认 15
 *   load.maxP95Ms         P95 阈值，默认 500
 *   load.maxErrorRate     错误率阈值，默认 0.01
 *   load.username / load.password  默认 admin / password
 *
 * 每个并发用户独立登录、独立 JSESSIONID，按权重混合调用读接口：
 *   snapshots(内存) / latest(库) / range(库) / alarms(库) / devices(内存)
 */
class ApiLoadTest {

    private static final String BASE_URL = System.getProperty("load.baseUrl", "http://localhost:8080");
    private static final String USERNAME = System.getProperty("load.username", "admin");
    private static final String PASSWORD = System.getProperty("load.password", "password");

    private static final int USERS = Integer.getInteger("load.users", 20);
    private static final int WARMUP_SECONDS = Integer.getInteger("load.warmupSeconds", 3);
    private static final int DURATION_SECONDS = Integer.getInteger("load.durationSeconds", 15);
    private static final double MAX_ERROR_RATE = Double.parseDouble(System.getProperty("load.maxErrorRate", "0.01"));
    private static final double MAX_P95_MS = Double.parseDouble(System.getProperty("load.maxP95Ms", "500"));

    @Test
    void loadTest() throws Exception {
        Assumptions.assumeTrue(serverReachable(),
                "应用未启动（" + BASE_URL + "），请先运行 DongHuanApplication 再执行本用例");

        List<Target> targets = buildTargets();
        int totalWeight = targets.stream().mapToInt(Target::weight).sum();

        long startNanos = System.nanoTime();
        long measureNanos = startNanos + TimeUnit.SECONDS.toNanos(WARMUP_SECONDS);
        long endNanos = measureNanos + TimeUnit.SECONDS.toNanos(DURATION_SECONDS);

        System.out.printf(Locale.ROOT, "%n===== 压测开始：%d 并发，预热 %ds，压测 %ds =====",
                USERS, WARMUP_SECONDS, DURATION_SECONDS);

        ExecutorService pool = Executors.newFixedThreadPool(USERS);
        List<Future<WorkerResult>> futures = new ArrayList<>(USERS);
        for (int i = 0; i < USERS; i++) {
            final int workerId = i;
            futures.add(pool.submit(() -> runWorker(workerId, targets, totalWeight, measureNanos, endNanos)));
        }
        pool.shutdown();
        if (!pool.awaitTermination(WARMUP_SECONDS + DURATION_SECONDS + 60L, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            fail("压测线程未在预期时间内结束");
        }

        // 汇总各 worker 的采样结果
        Map<String, List<Long>> latencies = new LinkedHashMap<>();
        Map<String, Integer> endpointErrors = new HashMap<>();
        int total = 0;
        int failed = 0;
        for (Future<WorkerResult> future : futures) {
            WorkerResult result;
            try {
                result = future.get();
            } catch (Exception e) {
                fail("压测线程异常：" + e.getCause());
                return;
            }
            total += result.total;
            failed += result.errors;
            result.latencies.forEach((k, v) -> latencies.computeIfAbsent(k, x -> new ArrayList<>()).addAll(v));
            result.endpointErrors.forEach((k, v) -> endpointErrors.merge(k, v, Integer::sum));
        }

        long[] allNanos = latencies.values().stream()
                .flatMap(List::stream)
                .mapToLong(Long::longValue)
                .sorted()
                .toArray();
        double errorRate = total == 0 ? 1.0 : failed / (double) total;
        double qps = total / (double) DURATION_SECONDS;
        double p95 = percentile(allNanos, 95);

        System.out.println();
        System.out.println("========== 压测结果 ==========");
        System.out.printf(Locale.ROOT, "目标      %s%n", BASE_URL);
        System.out.printf(Locale.ROOT, "并发      %d%n", USERS);
        System.out.printf(Locale.ROOT, "窗口      预热 %ds + 压测 %ds%n", WARMUP_SECONDS, DURATION_SECONDS);
        System.out.printf(Locale.ROOT, "总请求    %d%n", total);
        System.out.printf(Locale.ROOT, "失败      %d (%.2f%%)%n", failed, errorRate * 100);
        System.out.printf(Locale.ROOT, "吞吐      %.0f req/s%n", qps);
        System.out.printf(Locale.ROOT, "延迟 ms   P50 %.1f | P90 %.1f | P95 %.1f | P99 %.1f | max %.1f%n",
                percentile(allNanos, 50), percentile(allNanos, 90), p95, percentile(allNanos, 99),
                allNanos.length == 0 ? 0 : allNanos[allNanos.length - 1] / 1_000_000.0);

        System.out.println();
        System.out.println("接口明细");
        for (Map.Entry<String, List<Long>> entry : latencies.entrySet()) {
            long[] endpointNanos = entry.getValue().stream().mapToLong(Long::longValue).sorted().toArray();
            System.out.printf(Locale.ROOT, "  %-42s 请求 %7d  错误 %4d  P95 %8.1f ms%n",
                    entry.getKey(), endpointNanos.length,
                    endpointErrors.getOrDefault(entry.getKey(), 0), percentile(endpointNanos, 95));
        }
        System.out.println("==============================");

        assertTrue(total > 0, "压测未产生任何请求");
        assertTrue(errorRate <= MAX_ERROR_RATE, String.format(Locale.ROOT,
                "错误率 %.2f%% 超过阈值 %.2f%%，查看应用日志（连接池 / 异常堆栈）", errorRate * 100, MAX_ERROR_RATE * 100));
        assertTrue(p95 <= MAX_P95_MS, String.format(Locale.ROOT,
                "P95 %.1f ms 超过阈值 %.1f ms", p95, MAX_P95_MS));
    }

    /** 压测目标：名称 + 权重 + 请求路径 */
    private record Target(String name, int weight, Supplier<String> path) {
    }

    /**
     * 读接口混合比例，贴近大棚看板的真实访问：
     * 快照列表最高频，其次单点最新值，历史曲线次之。
     */
    private static List<Target> buildTargets() {
        String start = Instant.now().minus(Duration.ofHours(24)).toString();
        String end = Instant.now().toString();
        return List.of(
                new Target("GET /api/snapshots", 40, () -> "/api/snapshots"),
                new Target("GET /api/points/{d}/{p}/latest", 20, () -> "/api/points/th-01/temperature/latest"),
                new Target("GET /api/points/{d}/{p}/range", 15,
                        () -> "/api/points/th-01/temperature/range?start=" + start + "&end=" + end),
                new Target("GET /api/alarms/active", 15, () -> "/api/alarms/active"),
                new Target("GET /api/devices", 10, () -> "/api/devices")
        );
    }

    private static Target pick(List<Target> targets, SplittableRandom random, int totalWeight) {
        int r = random.nextInt(totalWeight);
        for (Target target : targets) {
            r -= target.weight();
            if (r < 0) return target;
        }
        return targets.get(targets.size() - 1);
    }

    private static WorkerResult runWorker(int workerId, List<Target> targets, int totalWeight,
                                          long measureNanos, long endNanos) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();

        int loginStatus = login(client);
        if (loginStatus != 200) {
            throw new IllegalStateException("压测账号登录失败，HTTP " + loginStatus
                    + "（检查 load.username / load.password，以及应用是否已初始化种子数据）");
        }

        WorkerResult result = new WorkerResult();
        SplittableRandom random = new SplittableRandom(workerId * 31L + 7);
        while (System.nanoTime() < endNanos) {
            Target target = pick(targets, random, totalWeight);
            long begin = System.nanoTime();
            int status;
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + target.path().get()))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            } catch (Exception e) {
                status = -1; // 连接拒绝 / 超时 / 读响应异常，计为失败
            }
            // 预热阶段的请求只建立连接与 JIT，不计入统计
            if (begin >= measureNanos) {
                result.record(target.name(), System.nanoTime() - begin, status);
            }
        }
        return result;
    }

    private static int login(HttpClient client) throws Exception {
        String body = String.format(Locale.ROOT, "{\"username\":\"%s\",\"password\":\"%s\"}", USERNAME, PASSWORD);
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/api/auth/login"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static boolean serverReachable() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/api/auth/me"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
            return true; // 401 也说明服务在
        } catch (Exception e) {
            return false;
        }
    }

    private static double percentile(long[] sortedNanos, double p) {
        if (sortedNanos.length == 0) return 0;
        int index = (int) Math.ceil(p / 100.0 * sortedNanos.length) - 1;
        index = Math.max(0, Math.min(index, sortedNanos.length - 1));
        return sortedNanos[index] / 1_000_000.0;
    }

    private static final class WorkerResult {
        private final Map<String, List<Long>> latencies = new HashMap<>();
        private final Map<String, Integer> endpointErrors = new HashMap<>();
        private int total;
        private int errors;

        private void record(String endpoint, long nanos, int status) {
            total++;
            latencies.computeIfAbsent(endpoint, k -> new ArrayList<>(4096)).add(nanos);
            if (status != 200) {
                errors++;
                endpointErrors.merge(endpoint, 1, Integer::sum);
            }
        }
    }
}
