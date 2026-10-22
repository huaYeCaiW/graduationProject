package com.donghuan.common;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

//一台设备的配置，对应devices.yml里devices列表的每个元素
@Data
public class DeviceConfig {

    private String id;                             //设备Id
    private String name;
    private String protocol;                       //协议mqtt/modbus
    private Connection connection;                 //连接信息
    private List<Point> points = new ArrayList<>();//测点列表

    //连接信息，字段随协议不同而不同
    @Data
    public static class Connection {
        private String topic;
    }

    @Data
    public static class Point {
        private String pointId;     //测点Id
        private String metric;      //指标名
        private String unit;        //单位
        private String jsonPath;    //JSON路径
        private String freq;        //采集频率
        private String timeJsonPath;// 采样时间路径
        private Double min;
        private Double max;
    }

    // 查测点
    public Optional<Point> findPoint(String pointId) {
        return points.stream().filter(p -> p.getPointId().equals(pointId))
                .findFirst();
    }
}
