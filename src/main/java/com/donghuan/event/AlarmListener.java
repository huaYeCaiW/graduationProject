package com.donghuan.event;

import com.donghuan.service.AlarmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmListener {

    private final AlarmService alarmService;

    @EventListener
    public void onAlarm(AlarmEvent event) {
        //警告入库
        alarmService.record(event);

        log.warn("越限警告触发：device={}, point={}, value={}, time={}",
                event.getPointValue().getDeviceId(),
                event.getPointValue().getPointId(),
                event.getPointValue().getValue(),
                event.getAlarmTime());
    }
}
