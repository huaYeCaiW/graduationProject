package com.donghuan.pipeline;

import com.donghuan.common.DeviceProperties;
import com.donghuan.model.LimitType;
import com.donghuan.model.PointValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThresholdChecker {
    private final DeviceProperties deviceProperties;

    //阈值判断
    public Optional<ThresholdResult> check(PointValue pv){

                    //找到测点配置
        return deviceProperties.findDevice(pv.getDeviceId())
                .flatMap(d -> d.findPoint(pv.getPointId()))
                .map(point -> {
                    //找到配置后判断
                    if (point.getMin() != null && pv.getValue() < point.getMin()) {
                        return new ThresholdResult(true, LimitType.MIN, point.getMin());
                    }
                    if (point.getMax() != null && pv.getValue() > point.getMax()) {
                        return new ThresholdResult(true, LimitType.MAX, point.getMax());
                    }
                    return ThresholdResult.normal();
                });
    }

    public record ThresholdResult(boolean breached, LimitType limitType, double threshold) {
        public ThresholdResult {
            if (breached && !Double.isFinite(threshold)) throw new IllegalArgumentException("阈值必须为数值");
        }

        public static ThresholdResult normal() {
            return new ThresholdResult(false, null, 0);
        }
    }
}
