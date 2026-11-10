package com.donghuan.model;

import lombok.Data;

import java.time.Instant;

//统一的数据模型，多元数据最终都归为这个形态
@Data
public class PointValue {
    private String deviceCode;      // 设备编码，对应 device.device_code
    private String pointCode;       // 测点编码，对应 point.point_code
    private String metric;          //测点名称
    private double value;
    private String unit;            //单位
    private Instant timestamp;      //数据时间
    private Instant sampledAt;      // 设备采样时间
    private String quality;         //质量码：正常/越限/异常
}
