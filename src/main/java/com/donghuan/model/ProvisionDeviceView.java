package com.donghuan.model;

/**
 * 开通任务详情里的已纳入设备
 */
public record ProvisionDeviceView(
        String deviceCode,
        String name,
        String protocol,
        int pointCount
) {
}
