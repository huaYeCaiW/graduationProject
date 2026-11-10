package com.donghuan.store;

import com.donghuan.model.AlarmValue;
import com.donghuan.model.TenantScope;

import java.time.Instant;
import java.util.List;

// 警告->收敛->自动恢复->人工确认
public interface AlarmStore {
    // alarm入库
    long insert(AlarmValue alarm);
    // 持续越限刷新
    void touch(long id, double value);
    // 恢复正常
    void markRecovered(long id, Instant recoveredAt);
    // 人工确认
    boolean confirm(TenantScope scope, long alarmId, long userId, Instant confirmedAt);
    // 启动查 alarm 仅供回填
    List<AlarmValue> findAllActive();
    // 查实时 ACTIVE
    List<AlarmValue> findActive(TenantScope scope);
    // 查警告记录
    List<AlarmValue> findAll(TenantScope scope);

}
