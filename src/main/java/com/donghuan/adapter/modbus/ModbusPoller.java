package com.donghuan.adapter.modbus;

import com.donghuan.common.DeviceConfig;
import com.donghuan.model.Protocol;
import com.donghuan.model.RawMessage;
import com.donghuan.stream.MessageStream;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 单设备一轮轮询：只读寄存器，不解码
@Slf4j
public class ModbusPoller {

    private final DeviceConfig device;
    private final ModbusTcpClient client;
    private final MessageStream messageStream;
    private final ObjectMapper objectMapper;
    // 每个测点上次读取时间，用于按各自 freq 决定本轮是否读取
    private final Map<String, Instant> lastRead = new ConcurrentHashMap<>();

    public ModbusPoller(DeviceConfig device, ModbusTcpClient client,
                        MessageStream messageStream, ObjectMapper objectMapper) {
        this.device = device;
        this.client = client;
        this.messageStream = messageStream;
        this.objectMapper = objectMapper;
    }

    public void poll() {
        String deviceCode = device.getDeviceCode();
        Map<String, int[]> snapshot = new LinkedHashMap<>();
        Map<String, Instant> readAt = new HashMap<>();
        boolean linkBroken = false;

        Instant now = Instant.now();
        for (DeviceConfig.Point point : device.getPoints()) {
            // 与当前协议不兼容的测点保留在内存但不采集
            if (point.isInvalid() || !due(point, now)) {
                continue;
            }
            try {
                int quantity = point.getRegisterType().isBit()
                        ? 1
                        : point.getDataType().registerCount();
                int[] words = client.read(point.getRegisterType(), point.getAddress(), quantity);
                snapshot.put(point.getPointCode(), words);
                readAt.put(point.getPointCode(), now);

            } catch (ModbusTcpClient.ModbusException e) {
                // 从站回了异常码，链路是通的，只跳过这个测点
                log.warn("Modbus 读取异常：deviceCode={}，pointCode={}，{}",
                        deviceCode, point.getPointCode(), e.getMessage());
            } catch (IOException e) {
                // 连接级失败，本轮放弃；close 后下一轮自动重连
                log.warn("Modbus 通信失败，本轮跳过：deviceCode={}，原因={}", deviceCode, e.getMessage());
                client.close();
                linkBroken = true;
                break;
            } catch (Exception e) {
                log.warn("Modbus 测点处理失败：deviceCode={}，pointCode={}",
                        deviceCode, point.getPointCode(), e);
            }
        }

        if (linkBroken || snapshot.isEmpty()) {
            return;
        }

        try {
            RawMessage raw = new RawMessage();
            raw.setDeviceCode(deviceCode);
            raw.setProtocol(Protocol.MODBUS);
            // 原始寄存器值以 JSON 进入系统，解码由解析层负责
            raw.setPayload(objectMapper.writeValueAsString(snapshot));
            raw.setReceivedAt(Instant.now());
            messageStream.emit(raw);
            // 报文成功进入系统后才记读取时间，避免本轮丢弃时下一轮误跳过
            lastRead.putAll(readAt);

            if (log.isDebugEnabled()) {
                log.debug("Modbus 轮询完成：deviceCode={}，payload={}", deviceCode, raw.getPayload());
            }
        } catch (Exception e) {
            log.warn("Modbus 报文组装失败：deviceCode={}", deviceCode, e);
        }
    }

    // 按测点配置的采集周期判断本轮是否读取；未配置则每轮都读
    private boolean due(DeviceConfig.Point point, Instant now) {
        Integer freq = point.getFreq();
        if (freq == null || freq <= 0) {
            return true;
        }
        Instant last = lastRead.get(point.getPointCode());
        return last == null || Duration.between(last, now).getSeconds() >= freq;
    }
}