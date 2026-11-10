package com.donghuan.model;

import lombok.Data;
import java.time.Instant;

//原始未处理数据
@Data
public class RawMessage {
    private String deviceCode;      // 设备编码，对应 device.device_code
    private Protocol protocol;        //协议类型
    private String payload;         //原始内容，String意在对于初始数据作宽松处理
    private Instant receivedAt;
    private String topic;//接收时间
}
