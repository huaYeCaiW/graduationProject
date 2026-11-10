package com.donghuan.service;

import com.donghuan.model.LoginUser;
import com.donghuan.model.PointValue;
import com.donghuan.model.TenantScope;
import com.donghuan.store.PointStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PointService {
    private final PointStore pointStore;

    public PointValue findLatest(LoginUser user, String deviceCode, String pointCode) {
        TenantScope scope = TenantScope.of(user);
        return pointStore.findLatest(scope, deviceCode, pointCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public List<PointValue> findByRange(LoginUser user, String deviceCode, String pointCode, Instant startTime, Instant endTime) {
        TenantScope scope = TenantScope.of(user);
        return pointStore.findByRange(scope, deviceCode, pointCode, startTime, endTime);
    }

    public void updateThreshold(LoginUser user, String deviceCode, String pointCode, Double min, Double max) {
        TenantScope scope = TenantScope.of(user);
        boolean ok = pointStore.updateThreshold(scope, deviceCode, pointCode, min, max);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
