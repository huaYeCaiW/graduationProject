package com.donghuan.adapter.mqtt;

import com.donghuan.adapter.ProtocolAdapter;
import com.donghuan.common.DeviceConfig;
import com.donghuan.model.Protocol;
import com.donghuan.model.RawMessage;
import com.donghuan.stream.MessageStream;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MqttProtocolAdapter implements ProtocolAdapter {

    // Paho客户端
    private final Map<String, MqttClient> clients = new ConcurrentHashMap<>();
    private final MessageStream messageStream;

    @Value("${mqtt.broker:tcp://localhost:1883}")
    private String broker;

    public MqttProtocolAdapter(MessageStream messageStream) {
        this.messageStream = messageStream;
    }

    @Override
    public Protocol protocol() {
        return Protocol.MQTT;
    }

    @Override
    public void connect(DeviceConfig device) {
        // deviceId 做key
        String deviceId = device.getId();
        MqttClient client = null;

        try {
            MqttClient existing = clients.get(deviceId);
            // 已连接
            if (existing != null && existing.isConnected()) {
                return;
            }
            // 存在未连接
            if (existing != null) {
                disconnect(deviceId);
            }
            // Paho 客户端id
            String clientId = "donghuan_java_" + deviceId;
            client = new MqttClient(broker, clientId);

            MqttClient bound = client;
            client.setCallback(new MqttCallbackExtended() {

                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    String topic = device.getConnection().getTopic();
                    try {
                        bound.subscribe(topic, 1);
                        if (reconnect) {
                            log.info("MQTT重连成功，已重新订阅：topic={}, deviceId={}", topic, deviceId);
                        }
                    } catch (MqttException e) {
                        log.warn("重新订阅失败：topic={}, deviceId={}", topic, deviceId);
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT断开连接，paho自动重连：{}", cause.getMessage());
                }

                // 回调签名
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    log.debug("收到MQTT：topic={}，payload={}", topic, payload);

                    //组装raw
                    RawMessage raw = new RawMessage();
                    raw.setDeviceId(device.getId());
                    raw.setProtocol(Protocol.MQTT);
                    raw.setTopic(topic);
                    raw.setPayload(payload);
                    raw.setReceivedAt(Instant.now());

                    messageStream.emit(raw);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {

                }
            });

            // 连接选项
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(false);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(60);

            // 连broker
            bound.connect(options);

            clients.put(deviceId, client);
            log.info("√ MQTT连接成功：broker={}，topic={}，deviceId={}", broker, device.getConnection().getTopic(), device.getId());

        } catch (MqttException e) {
            log.error("× MQTT连接失败：{}", e.getMessage());
            if (client != null) {
                try {
                    client.close();
                } catch (MqttException ignored) {}
            }
        }
    }

    @Override
    public void disconnect(String deviceId) {
        MqttClient client = clients.remove(deviceId);
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            log.info("已断开MQTT：deviceId={}", deviceId);
        } catch (MqttException e) {
            log.warn("MQTT断开异常：{}", e.getMessage());
        } finally {
            try {
                client.close();
            } catch (MqttException ignored) {

            }
        }
    }

    @PreDestroy
    public void closeAll() {
        clients.keySet().forEach(this::disconnect);
    }
}
