package com.donghuan.model;

/**
 * 开通时单个测点的接入配置；未填的阈值/量程/周期沿用指标模板
 */
public record ProvisionPointCommand(
        String metricCode,
        String address,
        String timeAddress,
        String dataType,
        Double minValue,
        Double maxValue,
        Double rangeMin,
        Double rangeMax,
        Integer freqSeconds
) {
}
