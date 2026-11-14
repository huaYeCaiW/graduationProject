package com.donghuan.model;

import java.util.List;

/**
 * 开通时一台大棚的登记信息和测点接入配置
 */
public record GreenhouseCommand(
        String deviceCode,
        String name,
        String protocol,
        String connectionConfig,
        List<ProvisionPointCommand> points
) {
}
