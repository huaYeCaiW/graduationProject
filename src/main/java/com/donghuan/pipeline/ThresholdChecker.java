package com.donghuan.pipeline;

import com.donghuan.common.DeviceConfig;
import com.donghuan.common.DeviceProperties;
import com.donghuan.event.AlarmEvent;
import com.donghuan.model.PointValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThresholdChecker {
    private final DeviceProperties deviceProperties;
    private final ApplicationEventPublisher eventPublisher;

    //阈值判断
    public boolean isOutOfRange(PointValue pv){

        //找到测点配置
        DeviceConfig.Point point = deviceProperties.findDevice(pv.getDeviceId())
                .flatMap(d -> d.findPoint(pv.getPointId()))
                .orElse(null);
        if (point == null) {
            return false;
        }
        //找到配置后判断
        if (point.getMin() != null && pv.getValue() < point.getMin()) return true;
        return point.getMax() != null && pv.getValue() > point.getMax();
    }

    //发布警告时间
    public void raiseAlarm(PointValue pv) {
        AlarmEvent alarm = new AlarmEvent(this, pv);
        eventPublisher.publishEvent(alarm);
        log.warn("越限警告：{} 值={}", pv.getPointId(), pv.getValue());
    }
}
