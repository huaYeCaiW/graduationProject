package com.donghuan.web.controller;

import com.donghuan.common.AuthConstants;
import com.donghuan.common.DeviceConfig;
import com.donghuan.model.LoginUser;
import com.donghuan.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {
    private final DeviceService deviceService;

    // 取所有设备
    @GetMapping
    public Collection<DeviceConfig> list(@RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return deviceService.list(user);
    }

    // {deviceCode} 是占位符——浏览器请求 /api/devices/th-01 时
    // Spring 把 "th-01" 这个字符串塞给方法参数 @PathVariable
    @GetMapping("/{deviceCode}")
    public DeviceConfig get(@PathVariable String deviceCode,
                            @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return deviceService.get(user, deviceCode);
    }
}
