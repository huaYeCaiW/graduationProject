package com.donghuan.store;

import com.donghuan.model.PointValue;
import com.donghuan.model.TenantScope;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PointStore {

    void save(PointValue value);

    Optional<PointValue> findLatest(TenantScope scope, String deviceCode, String pointCode);

    List<PointValue> findByRange(TenantScope scope, String deviceCode, String pointCode, Instant startTime, Instant endTime);

    boolean updateThreshold(TenantScope scope, String deviceCode, String pointCode, Double min, Double max);
}
