package com.donghuan.parser;

import com.donghuan.common.DeviceConfig;
import com.donghuan.common.DeviceProperties;
import com.donghuan.model.PointValue;
import com.donghuan.model.RawMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageParser {

    private final DeviceProperties deviceProperties;
    private final JsonPathPointExtractor extractor;


    public List<PointValue>  parse(RawMessage raw) {
        DeviceConfig device = deviceProperties.findDevice(raw.getDeviceId())
                .orElse(null);

        if(device == null){
            return Collections.emptyList();
        }

        List<PointValue> result = new ArrayList<>();

        for (DeviceConfig.Point point : device.getPoints()){  //遍历测点
            //抽值
            Optional<Double> opt = extractor.extract(
                    raw.getPayload(),
                    point.getJsonPath());

            Optional<Double> optTs = extractor.extract(
                    raw.getPayload(),
                    point.getTimeJsonPath());

            if (opt.isEmpty()){
                continue;
            }

            Instant sampleAt = optTs.isEmpty() ? raw.getReceivedAt() : Instant.ofEpochMilli(optTs.get().longValue());
            //组装Value
            PointValue pv = new PointValue();
            pv.setDeviceId(raw.getDeviceId());
            pv.setMetric(point.getMetric());
            pv.setPointId(point.getPointId());
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
