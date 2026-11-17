package com.donghuan.store;

import com.donghuan.model.domain.PointValue;
import com.donghuan.model.security.TenantScope;

import java.util.Map;

public interface LatestValueStore {
    void snapshot(PointValue pv);

    Map<String, PointValue> findAllSnapshots(TenantScope scope);
}
