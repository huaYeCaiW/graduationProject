package com.donghuan.model;

/**
 * 服务指标模板：开通时按这里的编码、名称、单位、默认阈值生成测点骨架
 */
public record ServiceMetricTemplate(
        String metricCode,
        String name,
        String unit,
        String dataType,
        Double defaultMin,
        Double defaultMax,
        Double rangeMin,
        Double rangeMax,
        Integer freqSeconds
) {
}
