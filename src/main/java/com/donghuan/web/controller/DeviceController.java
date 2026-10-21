package com.donghuan.web.controller;

import com.donghuan.common.DeviceConfig;
import com.donghuan.common.DeviceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {
    private final DeviceProperties deviceProperties;

    // 取所有设备
    @GetMapping
    public List<DeviceConfig> list() {
        return deviceProperties.getDevices();
    }

    // {deviceId} 是占位符——浏览器请求 /api/devices/th-01 时
    // Spring 把 "th-01" 这个字符串塞给方法参数 @PathVariable
    @GetMapping("/{deviceId}")
    public ResponseEntity<?> get(@PathVariable String deviceId) {
        return deviceProperties.findDevice(deviceId).map(ResponseEntity :: ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
