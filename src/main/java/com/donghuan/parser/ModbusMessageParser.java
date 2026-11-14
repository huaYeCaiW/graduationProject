package com.donghuan.parser;

import com.donghuan.common.DeviceConfig;
import com.donghuan.model.PointValue;
import com.donghuan.model.Protocol;
import com.donghuan.model.RawMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// 解析 {"pointCode":[寄存器值]}，按测点配置解码并缩放
@Slf4j
@Component
@RequiredArgsConstructor
public class ModbusMessageParser implements ProtocolMessageParser {

    private final ObjectMapper objectMapper;

    @Override
    public Protocol protocol() {
        return Protocol.MODBUS;
    }

    @Override
    public List<PointValue> parse(RawMessage raw, DeviceConfig device) {
        JsonNode root;
        try {
            root = objectMapper.readTree(raw.getPayload());
        } catch (Exception e) {
            log.warn("Modbus 报文解析失败：deviceCode={}，payload={}",
                    raw.getDeviceCode(), raw.getPayload(), e);
            return Collections.emptyList();
        }

        List<PointValue> result = new ArrayList<>();

        for (DeviceConfig.Point point : device.getPoints()) {
            JsonNode node = root.path(point.getPointCode());
            if (!node.isArray() || node.size() == 0) {
                continue;
            }

            int[] words = new int[node.size()];
            for (int i = 0; i < words.length; i++) {
                words[i] = node.get(i).asInt();
            }

            try {
                double scale = point.getScale() == null ? 1D : point.getScale();
                double value = point.getDataType().decode(words) * scale;

                PointValue pv = new PointValue();
                pv.setDeviceCode(raw.getDeviceCode());
                pv.setMetric(point.getMetric());
                pv.setPointCode(point.getPointCode());
                pv.setValue(value);
                pv.setUnit(point.getUnit());
                pv.setTimestamp(raw.getReceivedAt());
                // Modbus 无设备时间戳，采样时间取采集时间
                pv.setSampledAt(raw.getReceivedAt());
                pv.setQuality(null);

                result.add(pv);
            } catch (Exception e) {
                log.warn("Modbus 测点解码失败：deviceCode={}，pointCode={}，words={}",
                        raw.getDeviceCode(), point.getPointCode(), Arrays.toString(words));
            }
        }
        return result;
    }
}