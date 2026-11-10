package com.donghuan.service;

import com.donghuan.common.DeviceConfig;
import com.donghuan.common.DeviceProperties;
import com.donghuan.model.LoginUser;
import com.donghuan.model.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class DeviceService {
    private final DeviceProperties deviceProperties;

    // 过滤后返回 list
    public Collection<DeviceConfig> list(LoginUser user) {
        TenantScope scope = TenantScope.of(user);
        return deviceProperties.getDevices().stream()
                // filter 每个 device canSee == true 通过
                .filter(d -> scope.canSee(d.getTenantId()))
                .toList();
    }

    // 过滤后查不到就是 404
    public DeviceConfig get(LoginUser user, String deviceCode) {
        TenantScope scope = TenantScope.of(user);
        return deviceProperties.findDevice(deviceCode)
                .filter(d -> scope.canSee(d.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
