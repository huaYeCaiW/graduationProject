package com.donghuan.stream;

import com.donghuan.common.DeviceProperties;
import com.donghuan.common.PointKeys;
import com.donghuan.model.PointValue;
import com.donghuan.model.Quality;
import com.donghuan.model.TenantScope;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotBroadcaster {

    // 增量按 500ms 合并一次，避免每条测点数据都触发一次推送
    private static final long FLUSH_MILLIS = 500;
    private static final long HEARTBEAT_SECONDS = 20;

    private final PointKeys keys;
    private final DeviceProperties deviceProperties;

    private final Map<String, PointValue> pending = new ConcurrentHashMap<>();
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "snapshot-sse");
                t.setDaemon(true);
                return t;
            });

    @PostConstruct
    void start() {
        scheduler.scheduleWithFixedDelay(this::flush, FLUSH_MILLIS, FLUSH_MILLIS, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(this::heartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    // 建立连接时先推全量快照，之后推 500ms 增量
    public SseEmitter subscribe(TenantScope scope, Map<String, PointValue> initial) {
        SseEmitter emitter = new SseEmitter(0L);
        Subscriber subscriber = new Subscriber(emitter, scope);

        emitter.onCompletion(() -> subscribers.remove(subscriber));
        emitter.onTimeout(() -> subscribers.remove(subscriber));
        emitter.onError(e -> subscribers.remove(subscriber));

        try {
            emitter.send(SseEmitter.event().data(initial));
        } catch (Exception e) {
            emitter.complete();
            return emitter;
        }
        subscribers.add(subscriber);
        log.info("SSE 快照推送已连接：订阅数={}", subscribers.size());
        return emitter;
    }

    // 只推正常值，同一测点一个批次内只保留最新一条
    public void publish(PointValue pv) {
        if (!Quality.OK.name().equals(pv.getQuality())) {
            return;
        }
        pending.put(keys.key(pv.getDeviceCode(), pv.getPointCode()), pv);
    }

    private void flush() {
        if (pending.isEmpty() || subscribers.isEmpty()) {
            pending.clear();
            return;
        }

        Map<String, PointValue> batch = new HashMap<>(pending);
        pending.clear();

        for (Subscriber subscriber : subscribers) {
            Map<String, PointValue> visible = filter(batch, subscriber.scope());
            if (visible.isEmpty()) {
                continue;
            }
            try {
                subscriber.emitter().send(SseEmitter.event().data(visible));
            } catch (Exception e) {
                subscribers.remove(subscriber);
                subscriber.emitter().completeWithError(e);
            }
        }
    }

    // 按租户过滤，越权数据不进入连接
    private Map<String, PointValue> filter(Map<String, PointValue> batch, TenantScope scope) {
        if (scope.seeAll()) {
            return batch;
        }
        Map<String, PointValue> result = new HashMap<>();
        batch.forEach((key, pv) -> deviceProperties.findDevice(pv.getDeviceCode())
                .filter(device -> scope.canSee(device.getTenantId()))
                .ifPresent(device -> result.put(key, pv)));
        return result;
    }

    // 注释行心跳，不触发前端 onmessage，只维持连接
    private void heartbeat() {
        for (Subscriber subscriber : subscribers) {
            try {
                subscriber.emitter().send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                subscribers.remove(subscriber);
            }
        }
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
        subscribers.forEach(subscriber -> subscriber.emitter().complete());
        subscribers.clear();
    }

    private record Subscriber(SseEmitter emitter, TenantScope scope) {
    }
}
