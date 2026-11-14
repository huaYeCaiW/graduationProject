package com.donghuan.parser;

import com.donghuan.common.DeviceConfig;
import com.donghuan.model.PointValue;
import com.donghuan.model.Protocol;
import com.donghuan.model.RawMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MqttMessageParser implements ProtocolMessageParser {

    private final JsonPathPointExtractor extractor;

    @Override
    public Protocol protocol() {
        return Protocol.MQTT;
    }

    @Override
    public List<PointValue> parse(RawMessage raw, DeviceConfig device) {
        List<PointValue> result = new ArrayList<>();

        for (DeviceConfig.Point point : device.getPoints()) {
            // 与当前协议不兼容的测点不参与解析
            if (point.isInvalid()) {
                continue;
            }
            // 抽值
            Optional<Double> opt = extractor.extract(raw.getPayload(), point.getJsonPath());
            if (opt.isEmpty()) {
                continue;
            }

            Optional<Double> optTs = extractor.extract(raw.getPayload(), point.getTimeJsonPath());
            Instant sampleAt = optTs.isEmpty()
                    ? raw.getReceivedAt()
                    : Instant.ofEpochMilli(optTs.get().longValue());

            PointValue pv = new PointValue();
            pv.setDeviceCode(raw.getDeviceCode());
            pv.setMetric(point.getMetric());
            pv.setPointCode(point.getPointCode());
            pv.setValue(opt.get());
            pv.setUnit(point.getUnit());
            pv.setTimestamp(raw.getReceivedAt());
            pv.setSampledAt(sampleAt);
            pv.setQuality(null);

            result.add(pv);
        }
        return result;
    }
}