package com.donghuan.model;

// 测点新增/修改请求：字段与 point 表列对齐
public record PointCommand(
        String pointCode,
        String name,
        String address,
        String timeAddress,
        String dataType,
        String unit,
        Integer freqSeconds,
        Double minValue,
        Double maxValue,
        Double rangeMin,
        Double rangeMax
) {
}
