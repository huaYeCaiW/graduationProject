package com.donghuan.store;

import com.donghuan.model.AlarmValue;

import java.time.Instant;
import java.util.List;

// 警告->收敛->自动恢复->人工确认
public interface AlarmStore {
    // alarm入库
    long insert(AlarmValue alarm);
    // 持续越限刷新
    void touch(long id, double value, Instant sampleAt);
    // 恢复正常
    void markRecovered(long id, Instant recoveredAt);
    // 人工确认
    void confirm(long id, long userId, Instant confirmedAt);
    // 启动查alarm
    List<AlarmValue> findActive();

    List<AlarmValue> findAll();
}
