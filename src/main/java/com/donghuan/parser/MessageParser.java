package com.donghuan.parser;

import com.donghuan.common.DeviceConfig;
import com.donghuan.common.DeviceProperties;
import com.donghuan.model.PointValue;
import com.donghuan.model.RawMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
        // 从deviceProperties中获取设备数据流.stream()，
        // 从中筛选.filter()d，id与RawMessage相同的设备
        DeviceConfig device = deviceProperties.getDevices().stream()
                .filter(d -> d.getId().equals(raw.getDeviceId()))
                .findFirst() //取出DeviceConfig
                .orElse(null);
//常规for循环写法
//        DeviceConfig device = null;
//
//        for (DeviceConfig d : deviceProperties.getDevices()){
//            if(d.getId().equals(raw.getDeviceId())){
//                device = d;
//                break;
//            }
//        }

        if(device == null){
            return Collections.emptyList();
        }

        List<PointValue> result = new ArrayList<>();

        for (DeviceConfig.Point point : device.getPoints()){  //遍历测点
            //抽值
            Optional<Double> opt = extractor.extract(raw.getPayload(), point.getJsonPath());

            if (opt.isEmpty()){
                continue;
            }

            //组装Value
            PointValue pv = new PointValue();
            pv.setDeviceId(raw.getDeviceId());
            pv.setMetric(point.getMetric());
            pv.setPointId(point.getPointId());
            pv.setValue(opt.get());
            pv.setUnit(point.getUnit());
            pv.setTimestamp(raw.getReceivedAt());
            pv.setQuality(null);

            result.add(pv);
        }
        return result;
    }
}
