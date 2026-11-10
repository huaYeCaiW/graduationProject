package com.donghuan.service;

import com.donghuan.model.LoginUser;
import com.donghuan.model.PointValue;
import com.donghuan.model.TenantScope;
import com.donghuan.store.PointSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SnapshotService {
    private final PointSnapshot snapshot;

    public Map<String, PointValue> findAllSnapshots(LoginUser user) {
        TenantScope scope = TenantScope.of(user);
        return snapshot.findAllSnapshots(scope);
    }

}
