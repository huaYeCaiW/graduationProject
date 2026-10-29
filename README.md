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

#### 情况 A：MySQL 运行在 Docker 中（推荐，本项目验证环境）

**1）确认容器名和密码**

```bash
docker ps --filter "name=mysql"
docker inspect mysql --format '{{range .Config.Env}}{{println .}}{{end}}' | grep MYSQL

# PowerShell
Get-Content sql/schema.sql | docker exec -i mysql mysql -u root --password=你的密码

# CMD / Linux / macOS
docker exec -i mysql mysql -u root --password=你的密码 < sql/schema.sql

docker exec -it mysql mysql -u root -p -e "USE donghuan; SHOW TABLES;"
```

#### 情况 B：宿主机已安装 MySQL 客户端

```bash
mysql -u root -p < sql/schema.sql

Get-Content sql/schema.sql | mysql -u root -p
```

### 2. 修改配置

#### 1）复制配置模板

仓库里只保留 `application.properties.example`，本地配置不进 Git。

```powershell
Copy-Item src/main/resources/application.properties.example src/main/resources/application.properties

# Linux / macOS
cp src/main/resources/application.properties.example src/main/resources/application.properties

```

#### 2）修改数据库与 MQTT 配置
编辑 src/main/resources/application.properties，按本地环境改这四项：
```bash
spring.datasource.url=jdbc:mysql://localhost:3306/donghuan?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=你的MySQL密码
mqtt.broker=tcp://localhost:1883
donghuan.store.type=memory # 内存版没问题再改jdbc
```

#### 3）验证配置能连上库
启动应用前，先单独确认账号密码和库名没问题：

```bash
docker exec -it mysql mysql -u root -p -e "USE donghuan; SELECT COUNT(*) FROM point;"
```

### 3. 启动 EMQX

#### 1）确认没有同名容器

`docker run --name emqx` 遇到已存在的同名容器会报 `Conflict`，先检查：

```bash
docker ps -a --filter "name=emqx"
```

有残留就先删：

```bash
docker rm -f emqx
```

#### 2）启动 EMQX

```bash
docker run -d --name emqx -p 1883:1883 -p 18083:18083 emqx/emqx:5.8.8
```

#### 3）确认启动成功

```bash
docker ps --filter "name=emqx"
```

### 4. 启动应用
mvn spring-boot:run

### 5. 启动模拟器（另开终端）
py simulator.py

### 6. 验证
curl http://localhost:8080/api/devices

curl http://localhost:8080/api/points/{deviceId}/{pointId}/latest

## 现接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/alarms/active | 全部 ACTIVE |
| GET | /api/alarms | 全部（含 RECOVERED） |
| POST | /api/alarms/{id}/confirm | 确认警告 |
| GET | /api/points/{deviceId}/{pointId}/latest | 最新值 |
| GET | /api/points/{deviceId}/{pointId}/range?start=&end= | 查某时间范围内值 |
| PUT | /api/points/{deviceId}/{pointId}/threshold | 修改阈值 |
| GET | /api/devices | 查所有设备 |
| GET | /api/devices/{deviceId} | 查某设备 |

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

├── acquire/     启动编排

├─ adapter/      协议适配器

├── common/      配置绑定与启动加载

├─ model/        数据对象

├── parse/       报文解析与测点抽取

├─ pipeline/     判定层

├─ service/      警告业务

├── store/       PointStore 双实现

├── stream/      Reactor 热流与订阅

└── web/controller/  REST 接口

## 已知限制
- 仅实现 MQTT 接入，Modbus 规划中
- 无鉴权与权限控制
- 无前端页面
- 单机部署，未做压测
- MqttProtocolAdapter 当前为单例，多设备连接需改为 Map<deviceId, MqttClient>（修复中）
