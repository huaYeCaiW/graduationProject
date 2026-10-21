package com.donghuan.adapter.mqtt;

import com.donghuan.adapter.ProtocolAdapter;
import com.donghuan.common.DeviceConfig;
import com.donghuan.model.RawMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Component
public class MqttProtocolAdapter implements ProtocolAdapter {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Random random = new Random();

    @Override
    public String protocol() {
        return "mqtt";
    }

    @Override
    public void connect(DeviceConfig device) {
        log.info("模拟MQTT连接设备：{}", device.getId());
    }

    @Override
    public void subscribe(DeviceConfig device, Consumer<RawMessage> sink) {
        scheduler.scheduleAtFixedRate(() -> {
            //随机Json
            double temp = 15 + random.nextDouble() * 20;    //15~35
            double hum = 30 + random.nextDouble() *50;      //30~80
            String payload = String.format("{\"temperature\":%.1f,\"humidity\":%.1f}", temp, hum);
            //构造RawMessage
            RawMessage raw = new RawMessage();
            raw.setDeviceId(device.getId());
            raw.setProtocol("mqtt");
            raw.setPayload(payload);
            raw.setReceivedAt(Instant.now());
            //回调推进
            sink.accept(raw);
            log.debug("模拟报文：{}", payload);
        }, 0, 10, TimeUnit.SECONDS);
    }

    @Override
    public void disconnect(String deviceId) {

    }
}
