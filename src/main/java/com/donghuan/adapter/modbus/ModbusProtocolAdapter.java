package com.donghuan.adapter.modbus;

import com.donghuan.adapter.ProtocolAdapter;
import com.donghuan.common.DeviceConfig;
import com.donghuan.model.ModbusPointSpec;
import com.donghuan.model.Protocol;
import com.donghuan.stream.MessageStream;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

@Slf4j
@Component
public class ModbusProtocolAdapter implements ProtocolAdapter {

    private static final int DEFAULT_POLL_SECONDS = 5;
    private static final int MIN_POLL_SECONDS = 1;

    private final MessageStream messageStream;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final Map<String, DeviceTask> tasks = new ConcurrentHashMap<>();

    public ModbusProtocolAdapter(MessageStream messageStream,
                                 ObjectMapper objectMapper,
                                 @Value("${modbus.pool-size:4}") int poolSize) {
        this.messageStream = messageStream;
        this.objectMapper = objectMapper;
        this.scheduler = Executors.newScheduledThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "modbus-poll");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public Protocol protocol() {
        return Protocol.MODBUS;
    }

    @Override
    public void connect(DeviceConfig device) {
        String deviceCode = device.getDeviceCode();
        if (device.getPoints().isEmpty()) {
            disconnect(deviceCode);
            return;
        }

        // 协议切换或测点修复后重新解析；已解析的有效测点不重复改字段
        if (!resolvePoints(device)) {
            disconnect(deviceCode);
            return;
        }

        DeviceTask existing = tasks.get(deviceCode);
        if (existing != null) {
            // 测点增删改后重算周期
            int period = pollPeriod(device);
            if (existing.period() != period) {
                existing.future().cancel(false);
                ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
                        existing.poller()::poll, period, period, TimeUnit.SECONDS);
                tasks.put(deviceCode,
                        new DeviceTask(existing.client(), existing.poller(), future, period));
                log.info("Modbus 轮询周期已更新：deviceCode={}，周期={}s", deviceCode, period);
            }
            return;
        }

        ModbusEndpoint endpoint;
        try {
            endpoint = ModbusEndpoint.parse(device.getConnection() == null
                    ? null
                    : device.getConnection().getConfig());
        } catch (IllegalArgumentException e) {
            log.warn("Modbus 连接配置无效，跳过设备：deviceCode={}，原因={}", deviceCode, e.getMessage());
            return;
        }

        ModbusTcpClient client = new ModbusTcpClient(endpoint.host(), endpoint.port(), endpoint.unitId());
        ModbusPoller poller = new ModbusPoller(device, client, messageStream, objectMapper);
        int period = pollPeriod(device);

        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
                poller::poll, 1, period, TimeUnit.SECONDS);

        tasks.put(deviceCode, new DeviceTask(client, poller, future, period));
        log.info("√ Modbus 开始轮询：deviceCode={}，{}:{}，unitId={}，周期={}s，测点={}个",
                deviceCode, endpoint.host(), endpoint.port(), endpoint.unitId(),
                period, device.getPoints().size());
    }

    @Override
    public void disconnect(String deviceCode) {
        DeviceTask task = tasks.remove(deviceCode);
        if (task == null) {
            return;
        }
        task.future().cancel(false);
        task.client().close();
        log.info("已停止 Modbus 轮询：deviceCode={}", deviceCode);
    }

    @PreDestroy
    public void closeAll() {
        tasks.keySet().forEach(this::disconnect);
        scheduler.shutdownNow();
    }

    // 解析测点地址并回填；无效测点保留在内存并标记，供页面查看和后续编辑修复
    private boolean resolvePoints(DeviceConfig device) {
        boolean anyValid = false;

        for (DeviceConfig.Point point : device.getPoints()) {
            // 已解析的有效测点直接保留，避免轮询线程读到半更新字段
            if (!point.isInvalid() && point.getRegisterType() != null) {
                anyValid = true;
                continue;
            }
            try {
                ModbusPointSpec spec = ModbusPointSpec.parse(
                        point.getAddressText(), point.getDataTypeCode());

                point.setRegisterType(spec.registerType());
                point.setAddress(spec.address());
                point.setDataType(spec.dataType());
                point.setScale(spec.scale());
                point.setInvalid(false);
                anyValid = true;
            } catch (IllegalArgumentException e) {
                point.setInvalid(true);
                log.warn("Modbus 测点配置无效，已跳过：deviceCode={}，pointCode={}，address={}，原因={}",
                        device.getDeviceCode(), point.getPointCode(),
                        point.getAddressText(), e.getMessage());
            }
        }

        if (!anyValid) {
            log.warn("Modbus 设备没有有效测点，跳过设备：deviceCode={}", device.getDeviceCode());
            return false;
        }
        return true;
    }

    // 取该设备有效测点最小采集周期，全空默认 5 秒，下限 1 秒
    private int pollPeriod(DeviceConfig device) {
        int min = device.getPoints().stream()
                .filter(point -> !point.isInvalid())
                .map(DeviceConfig.Point::getFreq)
                .filter(Objects::nonNull)
                .filter(f -> f > 0)
                .min(Integer::compareTo)
                .orElse(DEFAULT_POLL_SECONDS);
        return Math.max(min, MIN_POLL_SECONDS);
    }

    private record DeviceTask(ModbusTcpClient client, ModbusPoller poller,
                              ScheduledFuture<?> future, int period) {
    }
}