package com.donghuan.adapter.mqtt;

import com.donghuan.adapter.ProtocolAdapter;
import com.donghuan.common.DeviceConfig;
import com.donghuan.model.RawMessage;
import com.donghuan.stream.MessageStream;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.Consumer;

@Slf4j
@Component
public class MqttProtocolAdapter implements ProtocolAdapter {

    // Paho客户端
    private MqttClient client;
    private final MessageStream messageStream;

    @Value("${mqtt.broker:tcp://localhost:1883}")
    private String broker;

    public MqttProtocolAdapter(MessageStream messageStream) {
        this.messageStream = messageStream;
    }

    @Override
    public String protocol() {
        return "mqtt";
    }

    @Override
    public void connect(DeviceConfig device) {
        try {
            // 新建paho客户端clientId全局唯一
            String clientId = "donghuan_java_" + device.getId() +"_" + System.currentTimeMillis();
            this.client = new MqttClient(broker,clientId);

            // 没回调
            this.client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT断开连接，paho自动重连：{}", cause.getMessage());
                }

                // 回调签名
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String payload = new String(message.getPayload());
                    log.debug("收到MQTT：topic={}，payload={}", topic, payload);

                    //组装raw
                    RawMessage raw = new RawMessage();
                    raw.setDeviceId(device.getId());
                    raw.setProtocol("mqtt");
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
            this.client.connect(options);

            // 订阅topic
            this.client.subscribe(device.getConnection().getTopic(), 1);
            log.info("√ MQTT连接成功：broker={}，topic={}，deviceId={}", broker, device.getConnection().getTopic(), device.getId());

        } catch (MqttException e) {
            log.error("× MQTT连接失败：{}", e.getMessage());
        }
    }


    // TODO 4阶段实现
    @Override
    public void subscribe(DeviceConfig device, Consumer<RawMessage> sink) {

    }

    @Override
    @PreDestroy
    public void disconnect() {
        if (this.client != null && this.client.isConnected()) {
            try {
                this.client.disconnect();
                this.client.close();
                log.info("已断开");
            } catch (MqttException e) {
                log.warn("MQTT断开异常：{}", e.getMessage());
            }
        }
    }
}
