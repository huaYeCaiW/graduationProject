# 机房动环多元数据接入与处理系统

面向中小型机房的动力环境监控系统，解决多厂商设备协议不一致、告警重复风暴的问题。

当前实现：MQTT 接入、JsonPath 解析、Reactor 热流三路订阅、告警收敛与持久化、配置库化、JDBC 落库与内存快照。

技术栈：Spring Boot 4.0.8 / Java 21 / Reactor / MySQL 8.4 / MQTT(EMQX 5.8) / JdbcTemplate

## 环境

- JDK 21

- Maven 3.9+

- Docker（用于启动 MySQL 与 EMQX）

- （可选）Python 3.10+，用于跑 MQTT 模拟器

## 快速启动

### 1. 建库建表

**下载并安装 docker**

##### 1）. 打开 <https://www.docker.com/products/docker-desktop/>，下载 Docker Desktop for Windows。

安装完成后在 PowerShell / 终端确认：

```powershell
docker --version
docker compose version
```

##### 2）. 运行 compose 启动 MySQL 与 EMQX

进入项目根目录（Windows 示例）：

```powershell
cd C:\Learn\graduationProject\donghuan
docker compose up -d
```

首次执行会拉取 `mysql:8.4` 和 `emqx/emqx:5.8`，需要等几分钟。容器名分别是 `dh-mysql` 和 `dh-emqx`。

**数据卷为空时，MySQL 首次初始化会自动执行** **`schema.sql`** **和** **`seed.sql`**，所以全新环境这一步就是一键完成；已有数据卷会自动跳过，见下面的「3）确认初始化结果」。

确认状态：

```powershell
docker compose ps
```

##### 3）. 确认初始化结果

等 MySQL 变成 `healthy` 后直接验证：

```powershell
docker compose exec mysql mysql -uroot -p123456 -e "use donghuan; select id, point_code, unit from point;"
```

期望返回 2 条，`unit` 分别是 `℃` 和 `%`。

`seed.sql` 写入的内容：测试用户、设备 `th-01`、两个测点、4 条采样值、1 条 ACTIVE 告警。
ACTIVE 告警会在应用启动时被回填进内存；只想测“新告警插入”时，先删掉它再启动。

##### 4）. 重置数据（可选）

`down -v` 会删除数据卷（含 MySQL 与 EMQX 数据），再次 `up` 时自动重新建表并灌种子：

```powershell
docker compose down -v
docker compose up -d
```

### 2. 修改配置

#### 1）修改数据库用户名与密码

编辑 `src/main/resources/application.properties`，把占位符换成实际值：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/donghuan?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=123456
# 可选，默认就是 tcp://localhost:1883
# mqtt.broker=tcp://localhost:1883
```

说明：

- 数据库密码必须与 `docker-compose.yml` 里的 `MYSQL_ROOT_PASSWORD` 一致

- 采样数据按 UTC 存储，查询时传 `...Z` 或带偏移的时间

启动前先确认账号密码和库名可用：

```powershell
docker compose exec mysql mysql -uroot -p123456 -e "use donghuan; select count(*) from point;"
```

### 3. 启动应用

```powershell
mvn spring-boot:run
```

### 4. 启动模拟器（另开终端）

```powershell
py simulator.py
```

### 5. 验证

```bash
curl http://localhost:8080/api/devices
curl http://localhost:8080/api/snapshots
curl http://localhost:8080/api/points/th-01/temperature/latest
curl "http://localhost:8080/api/points/th-01/temperature/range?start=2026-11-06T00:00:00Z&end=2026-11-07T00:00:00Z"
curl http://localhost:8080/api/alarms/active
curl -X POST http://localhost:8080/api/alarms/1/confirm
```

EMQX 控制台：<http://localhost:18083>（`admin / public`）。

## 接口

| 方法   | 路径                                                  | 说明                               |
| ---- | --------------------------------------------------- | -------------------------------- |
| GET  | /api/alarms/active                                  | 全部 ACTIVE 告警                     |
| GET  | /api/alarms                                         | 全部告警（含 RECOVERED）                |
| POST | /api/alarms/{id}/confirm                            | 确认告警，返回影响行数（0 表示不存在或已恢复）         |
| GET  | /api/points/{deviceId}/{pointId}/latest             | 库中最新一条 OK 数据，无数据返回 404           |
| GET  | /api/points/{deviceId}/{pointId}/range?start=\&end= | 时间范围内的 OK 数据（ISO-8601）           |
| PUT  | /api/points/{deviceId}/{pointId}/threshold          | 修改阈值，body: `{"min":18,"max":38}` |
| GET  | /api/devices                                        | 全部设备（读数据库）                       |
| GET  | /api/devices/{deviceId}                             | 单个设备，不存在返回 404                   |
| GET  | /api/snapshots                                      | 全部测点的内存最新快照                      |

## 配置说明

配置已全部库化，设备与测点由 `device`、`point` 两张表驱动：

| 表.字段                          | 作用                            |
| ----------------------------- | ----------------------------- |
| device.device\_code           | 设备标识，接口与 MQTT topic 使用        |
| device.connection\_config     | MQTT topic，如 `donghuan/th/01` |
| device.enabled                | 停用后不加载、不接入                    |
| point.point\_code             | 测点标识，如 `temperature`          |
| point.address                 | 取值 JsonPath，如 `$.temperature` |
| point.time\_address           | 取样时间 JsonPath，如 `$.ts`        |
| point.unit                    | 单位，如 `℃`                      |
| point.min\_value / max\_value | 告警阈值                          |
| point.freq\_seconds           | 采样间隔字段（当前节流固定 3 秒，未使用该字段）     |
| point.enabled                 | 停用后不加载映射、不参与告警                |

`seed.sql` 已按 `th-01` 写好，开箱可用；也可以直接改库调整设备与测点。
报文缺 `ts` 时用服务器接收时间兜底。

## 项目结构

```
src/main/java/com/donghuan
├── acquire/        启动编排，按库中设备逐台接入
├── adapter/        ProtocolAdapter 接口；mqtt/ 为 Paho 实现
├── common/         设备配置（查库）、点位键
├── model/          PointValue / AlarmValue / RawMessage 及枚举
├── parser/         MessageParser 与 JsonPath 测点抽取
├── pipeline/       单位换算、校验、去重、阈值判定
├── service/        告警收敛与业务处理
├── store/          PointStore / AlarmStore、内存快照、点位映射；jdbc/ 为落库实现
├── stream/         Reactor 热流：快照 / 告警 / 存储三路订阅 + 3 秒节流
└── web/controller/ REST 接口
```

## 常见问题

**已有数据卷，初始化脚本没执行**
初始化脚本只在 `/var/lib/mysql` 为空时执行。可以 `docker compose down -v` 删卷重建，或手工导入：

```powershell
cmd /c "docker compose exec -T mysql mysql -uroot -p123456 < sql\schema.sql"
cmd /c "docker compose exec -T mysql mysql -uroot -p123456 < sql\seed.sql"
```

注意 `schema.sql` 的建表语句没有 `if not exists`，不能对已经建过表的库重复执行，否则会报 `table already exists`。

**导入 SQL 报 1064，或中文变成** **`?`**
PowerShell 的 `<` 重定向和 `Get-Content |` 管道默认按 ANSI 编码，会破坏中文和 `℃`，请用上面的 `cmd /c` 写法。

**改了 SQL 想重新灌数据**
`docker compose down -v` 后 `up -d`，初始化脚本会自动重跑。注意会删除全部数据。

**3306 / 1883 / 18083 端口被占用**
改 `docker-compose.yml` 的端口映射，同时同步修改 `application.properties` 里的数据库端口或 `mqtt.broker`。

**不想用 Docker 跑 MySQL**
可以用本机 MySQL 8，手工执行 `sql/schema.sql` 和 `sql/seed.sql`，再改 `application.properties` 的连接信息；EMQX 仍可用 compose 或单独容器启动。

## 已知限制

- 量程校验仍写死 `-50 ~ 150`，未使用库中配置

- 无鉴权与权限控制，确认接口固定使用 `user id=1`

- 无前端页面

- 单机单实例：快照、活跃告警、去重状态都在 JVM 内存，重启后快照与去重状态丢失（ACTIVE 告警会从库回填）

- 采样节流固定 3 秒，未读取 `freq_seconds`

- 0.1s 高频上报尚未压测

- `latest` / `range` 只返回 `quality=OK` 的数据，BAD 数据会入库但对查询不可见

- 设备断线不会产生“离线告警”，告警恢复依赖后续正常数据

