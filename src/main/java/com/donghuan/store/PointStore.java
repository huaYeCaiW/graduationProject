package com.donghuan.store;

import com.donghuan.model.PointValue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PointStore {

    void save(PointValue value);
    Optional<PointValue> findLatest(String deviceId, String pointId);
    List<PointValue> findByRange(String deviceId, String pointId, Instant startTime, Instant endTime);
}
