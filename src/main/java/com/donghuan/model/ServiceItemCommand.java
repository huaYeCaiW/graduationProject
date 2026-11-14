package com.donghuan.model;

import java.util.List;

/**
 * 服务项新增/修改命令
 */
public record ServiceItemCommand(
        String code,
        String name,
        String description,
        Integer maxGreenhouses,
        Integer maxPointsPerGreenhouse,
        Boolean enabled,
        List<ServiceMetricTemplate> metrics
) {
}
