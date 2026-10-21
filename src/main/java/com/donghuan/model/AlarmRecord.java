package com.donghuan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmRecord {

    private String deviceId;
    private String pointId;
    private String metric;
    private double value;
    private Double min;
    private Double max;
    private Instant alarmTime;
}
