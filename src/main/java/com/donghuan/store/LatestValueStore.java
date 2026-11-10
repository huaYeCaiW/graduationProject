package com.donghuan.store;

import com.donghuan.model.PointValue;
import com.donghuan.model.TenantScope;

import java.util.Map;

public interface LatestValueStore {
    void snapshot(PointValue pv);

    Map<String, PointValue> findAllSnapshots(TenantScope scope);
}
