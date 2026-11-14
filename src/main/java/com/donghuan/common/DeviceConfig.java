package com.donghuan.common;

import com.donghuan.model.ModbusDataType;
import com.donghuan.model.Protocol;
import com.donghuan.model.RegisterType;
import lombok.Data;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public class DeviceConfig {

    private String deviceCode;                     //设备编码，对应 device.device_code
    private String name;
    private Long tenantId;
    private Protocol protocol;                       //协议mqtt/modbus
    private Connection connection;                 //连接信息
    private List<Point> points = new CopyOnWriteArrayList<>();//测点列表，增删不影响正在遍历的线程

    //连接信息，字段随协议不同而不同
    @Data
    public static class Connection {
        private String config;
    }

    @Data
    public static class Point {
        private String pointCode;   //测点编码，对应 point.point_code
        private String metric;      //指标名
        private String unit;        //单位
        private String jsonPath;    //JSON路径
        private Integer freq;        //采集频率
        private String timeJsonPath;// 采样时间路径
        private Double min;
        private Double max;
        private Double rangeMin;    // 物理量程下限，null 表示不校验
        private Double rangeMax;    // 物理量程上限，null 表示不校验
        private boolean invalid = true; // 与当前协议不兼容或地址解析失败：保留在内存但不采集

        // Modbus
        private String addressText;      // point.address 原文
        private String dataTypeCode;     // point.data_type 原文
        private RegisterType registerType;  // 解析后：寄存器类型
        private Integer address;            // 解析后：寄存器起始地址
        private ModbusDataType dataType;    // 解析后：数据类型
        private Double scale;               // 解析后：缩放系数
    }

    // 查测点
    public Optional<Point> findPoint(String pointCode) {
        return points.stream().filter(p -> p.getPointCode().equals(pointCode))
                .findFirst();
    }
}
