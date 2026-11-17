package com.donghuan.model.view;

import com.donghuan.model.enums.AlarmStatus;
import com.donghuan.model.enums.LimitType;
import lombok.Data;

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
    private Long maintainedBy;
}
