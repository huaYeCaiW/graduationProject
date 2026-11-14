package com.donghuan.model;

// 设备新增/修改参数，字段与 device 表列对齐
// enabled 只在修改时使用：true 表示恢复逻辑删除的设备，其余场景忽略
public record DeviceCommand(
        String deviceCode,
        Long tenantId,
        String name,
        String protocol,
        String connectionConfig,
        Boolean enabled
) {
}
