# 机房动环多元数据接入与处理系统

面向中小型机房的动力环境监控系统，解决多厂商设备协议不一致、告警重复风暴的问题。
当前实现：MQTT 接入、JsonPath 解析、Reactor 热流三路订阅、告警收敛与持久化、阈值库化、内存/JDBC 双存储。
技术栈：Spring Boot 4.0.8 / Java 21 / Reactor / MySQL 8 / MQTT(EMQX) / JdbcTemplate

## 环境

- JDK 21
- Maven 3.9+
- MySQL 8.0+
- EMQX 5.8.8（或任意 MQTT Broker）
- （可选）Python 3.10+，用于跑 MQTT 模拟器

## 快速启动

### 1. 建库建表
mysql -u root -p < sql/schema.sql

### 2. 改配置
修改 src/main/resources/application.properties：
- spring.datasource.url / username / password
- mqtt.broker（默认 tcp://localhost:1883）
- donghuan.store.type=memory 或 jdbc

### 3. 启动 EMQX
docker run -d --name emqx -p 1883:1883 -p 18083:18083 emqx/emqx:5.8.8

### 4. 启动应用
mvn spring-boot:run

### 5. 启动模拟器（另开终端）
py simulator/mqtt_simulator.py

### 6. 验证
curl http://localhost:8080/api/devices

curl http://localhost:8080/api/points/{deviceId}/{pointId}/latest

## 现接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/devices | 设备列表 |
| GET | /api/devices/{deviceId} | 设备详情 |
| GET | /api/points/{deviceId}/{pointId}/latest | 最新值 |
| GET | /api/points/{deviceId}/{pointId}/range?start=&end= | 区间查询 |
| PUT | /api/points/{deviceId}/{pointId}/threshold | 修改阈值 |
| GET | /api/alarms | 告警列表 |
| PUT | /api/alarms/{id}/confirm | 确认告警 |

## 配置说明

devices:
  - id: th-01
    protocol: mqtt
    connection:
      topic: /device/th-01/data
    points:
      - pointId: temperature
        metric: 温度
        jsonPath: $.temperature
        unit: ℃
        min: 10
        max: 35

## 项目结构

src/main/java/...
├── config/      配置绑定与启动加载
├── acquisition/ 协议适配与设备接入
├── parse/       报文解析与测点抽取
├── stream/      Reactor 热流与订阅
├── alarm/       告警收敛与持久化
├── store/       PointStore 双实现
└── controller/  REST 接口

## 已知限制
- 仅实现 MQTT 接入，Modbus 规划中
- 无鉴权与权限控制
- 无前端页面
- 单机部署，未做压测
- MqttProtocolAdapter 当前为单例，多设备连接需改为 Map<deviceId, MqttClient>（修复中）
