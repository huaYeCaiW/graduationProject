package com.donghuan.model;

import java.util.List;

/**
 * 服务项视图：后台维护和种植户服务目录共用
 */
public record ServiceItemView(
        long id,
        String code,
        String name,
        String description,
        int maxGreenhouses,
        int maxPointsPerGreenhouse,
        boolean enabled,
        List<ServiceMetricTemplate> metrics
) {
}
