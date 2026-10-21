package com.donghuan.event;

import com.donghuan.model.PointValue;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

@Getter
public class AlarmEvent extends ApplicationEvent {

    private final PointValue pointValue;
    private final Instant alarmTime;

    public AlarmEvent(Object source, PointValue pointValue) {
        super(source);                      // 事件源
        this.pointValue = pointValue;       // 越限点
        this.alarmTime = Instant.now();     // 警告触发时刻
    }
}
