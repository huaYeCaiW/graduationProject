package com.donghuan.web.controller;

import com.donghuan.common.AuthConstants;
import com.donghuan.common.DeviceConfig;
import com.donghuan.model.DeviceCommand;
import com.donghuan.model.LoginUser;
import com.donghuan.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    // 已删除设备列表，供恢复入口选择；必须放在 /{deviceCode} 之前语义上更明确
    @GetMapping("/disabled")
    public Collection<DeviceCommand> findDisabled(
            @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return deviceService.findDisabled(user);
    }

    // {deviceCode} 是占位符——浏览器请求 /api/devices/th-01 时
    // Spring 把 "th-01" 这个字符串塞给方法参数 @PathVariable
    @GetMapping("/{deviceCode}")
    public DeviceConfig get(@PathVariable String deviceCode,
                            @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return deviceService.get(user, deviceCode);
    }

    // 新增设备
    @PostMapping
    public ResponseEntity<Void> create(@RequestBody DeviceCommand cmd) {
        deviceService.create(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // 修改设备
    @PutMapping("/{deviceCode}")
    public ResponseEntity<Void> update(@PathVariable String deviceCode,
                                       @RequestBody DeviceCommand cmd,
                                       @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        deviceService.update(user, deviceCode, cmd);
        return ResponseEntity.ok().build();
    }

    // 删除设备（逻辑删除）
    @DeleteMapping("/{deviceCode}")
    public ResponseEntity<Void> delete(@PathVariable String deviceCode,
                                       @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        deviceService.delete(user, deviceCode);
        return ResponseEntity.noContent().build();
    }
}
