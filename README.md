# 机房动环多元数据接入与处理系统 （项目更新README暂未更新）

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

**1）下载并安装 docker**
##### 1. 打开 <https://www.docker.com/products/docker-desktop/>，下载 Docker Desktop for Windows。

安装完成后在 powershell /终端确认
```powershell
docker --version
docker compose version
```
##### 2. 运行 compose 启动 MySQL 与 EMQX

进入项目根目录（Windows 示例）：
```powershell
cd C:\Learn\graduationProject\donghuan
docker compose up -d
```
首次执行会拉取 `mysql:8.4` 和 `emqx/emqx:5.8`，需要等几分钟。容器名分别是 `dh-mysql` 和 `dh-emqx`。

**数据卷为空时，MySQL 首次初始化会自动执行** **`schema.sql`** **和** **`seed.sql`**，所以全新环境这一步就是一键完成；已有数据卷会自动跳过，见 1.3。

确认状态：
```powershell
docker compose ps
```

##### 3. 确认初始化结果

全新容器首次启动会自动按顺序执行 `schema.sql` 和 `seed.sql`，等 MySQL 变成 `healthy` 后直接验证：
```powershell
docker compose exec mysql mysql -uroot -p123456 -e "use donghuan; select id, point_code, unit from point;"
```
期望返回 2 条，`unit` 分别是 `℃` 和 `%`。

`seed.sql` 写入的内容：测试用户、设备 `th-01`、两个测点、4 条采样值、1 条 ACTIVE 告警。
ACTIVE 告警会在应用启动时被回填进内存；只想测“新告警插入”时，先删掉它再启动。

##### 4. 重置数据（可选）

`down -v` 会删除数据卷（含 MySQL 与 EMQX 数据），再次 `up` 时自动重新建表并灌种子：
```powershell
docker compose down -v
docker compose up -d
```

### 2. 修改配置

#### 1）复制配置模板

编辑 `src/main/resources/application.properties`，把占位符换成实际值：

```Propertise
spring.datasource.url=jdbc:mysql://localhost:3306/donghuan?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=123456
mqtt.broker=tcp://localhost:1883
donghuan.store.type=jdbc
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
