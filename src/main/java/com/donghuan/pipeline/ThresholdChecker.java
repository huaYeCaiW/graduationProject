package com.donghuan.pipeline;

import com.donghuan.common.DeviceConfig;
import com.donghuan.common.DeviceProperties;
import com.donghuan.event.AlarmEvent;
import com.donghuan.model.PointValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThresholdChecker {
    private final DeviceProperties deviceProperties;
    private final ApplicationEventPublisher eventPublisher;

    //阈值判断
    public boolean isOutOfRange(PointValue pv){

        //找到测点配置
        DeviceConfig.Point point = findPoint(pv.getDeviceId(), pv.getPointId());
        if (point == null) {
            return false;
        }
        //找到配置后判断
        if (point.getMin() != null && pv.getValue() < point.getMin()) return true;
        if (point.getMax() != null && pv.getValue() > point.getMax()) return true;
        return false;
    }

    //发布警告时间
    public void raiseAlarm(PointValue pv) {
        AlarmEvent alarm = new AlarmEvent(this, pv);
        eventPublisher.publishEvent(alarm);
        log.warn("越限警告：{} 值={}", pv.getPointId(), pv.getValue());
    }

    private DeviceConfig.Point findPoint(String deviceId, String pointId) {
        // .stream()找到对应设备，再通过device.getPoints().stream()找到对应测点
        // 方法同MassageParser
        DeviceConfig device = deviceProperties.getDevices().stream()
                .filter(d -> d.getId().equals(deviceId))
                .findFirst()
                .orElse(null);
        DeviceConfig.Point point = null;
        if (device != null) {
            point = device.getPoints().stream()
                    .filter(p -> p.getPointId().equals(pointId))
                    .findFirst()
                    .orElse(null);
        }
        return point;
    }
}
