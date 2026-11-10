package com.donghuan.common;

import com.donghuan.model.Protocol;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
public class DeviceConfig {

    private String deviceCode;                     //设备编码，对应 device.device_code
    private String name;
    private Long tenantId;
    private Protocol protocol;                       //协议mqtt/modbus
    private Connection connection;                 //连接信息
    private List<Point> points = new ArrayList<>();//测点列表

    //连接信息，字段随协议不同而不同
    @Data
    public static class Connection {
        private String topic;
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
    }

    // 查测点
    public Optional<Point> findPoint(String pointCode) {
        return points.stream().filter(p -> p.getPointCode().equals(pointCode))
                .findFirst();
    }
}
