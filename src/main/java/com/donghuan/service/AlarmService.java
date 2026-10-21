package com.donghuan.service;

import com.donghuan.event.AlarmEvent;
import com.donghuan.model.PointValue;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AlarmService {
    //目前警告记录存入内存，后面连接数据库
    private final List<AlarmRecord> records = new CopyOnWriteArrayList<>();

    //记录
    public void record(AlarmEvent event) {
        AlarmRecord record = new AlarmRecord(event.getPointValue(), event.getAlarmTime());
        records.add(record);
    }

    public List<AlarmRecord> listAll() {
        //按时间排序
        return records.stream()
                .sorted(Comparator.comparing(AlarmRecord::getAlarmTime).reversed())
                .collect(Collectors.toList());
    }

    @Getter
    public static class AlarmRecord {
        private final String deviceId;
        private final String pointId;
        private final double value;
        private final Instant alarmTime;
        private final String metric;     // "温度"
        private Double min;       // 下限
        private Double max;       // 上限

        public AlarmRecord(PointValue pv, Instant alarmTime) {
            this.deviceId = pv.getDeviceId();
            this.pointId = pv.getPointId();
            this.alarmTime = alarmTime;
            this.value = pv.getValue();
            this.metric = pv.getMetric();
        }
    }
}
