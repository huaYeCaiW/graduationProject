package com.donghuan.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
public class AlarmValue {

    private Long id;
    private long pointId;
    private double value;
    private double threshold;
    private LimitType limitType;
    private AlarmStatus status = AlarmStatus.ACTIVE;
    private Instant sampleAt;
    private Instant createAt;
    private Long confirmedBy;
    private Instant confirmedAt;
    private Instant recoveredAt;
}
