# 机房动环多元数据接入与处理系统

面向中小型机房的动力环境监控系统，解决多厂商设备协议不一致、告警重复风暴的问题。

当前实现：MQTT 接入、JsonPath 解析、Reactor 热流三路订阅、数据清洗、告警收敛与持久化、配置库化、JDBC 落库。

技术栈：Spring Boot 4.0.8 / Java 21 / Reactor / MySQL 8.4 / MQTT(EMQX 5.8) / JdbcTemplate

## 环境

- JDK 21
- Maven 3.9+
- Docker（用于启动 MySQL 与 EMQX）
- （可选）Python 3.10+，用于跑 MQTT 模拟器

## 快速启动

### 1. 安装 Docker

**Windows**

1. 打开 <https://www.docker.com/products/docker-desktop/>，下载 Docker Desktop for Windows。
2. 双击安装，保持默认勾选 “Use WSL 2 instead of Hyper-V”。
3. 安装完成后重启电脑；首次启动若提示 WSL 内核版本过旧，按提示安装更新。
4. 等 Docker Desktop 左下角变成绿色（Engine running）。

**macOS**：同一页面下载 Docker Desktop for Mac，Apple 芯片选 Apple Silicon 版本。

**Linux**：

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER    # 重新登录后生效
```

安装完成后确认：

```powershell
docker --version
docker compose version
```

### 2. 启动 MySQL 与 EMQX

进入项目根目录，执行：

```powershell
cd C:\Learn\graduationProject\donghuan
docker compose up -d
```

首次执行会拉取 `mysql:8.4` 和 `emqx/emqx:5.8`，容器名分别是 `dh-mysql` 和 `dh-emqx`。

数据卷为空时，MySQL 首次初始化会自动执行 `schema.sql` 和 `seed.sql`；已有数据卷会跳过，见常见问题。

确认状态：

```powershell
docker compose ps
```

`mysql` 的 STATUS 要出现 `healthy`，出现前不要启动应用。一直 `starting` 就多等 10–30 秒；`unhealthy` 看日志：

```powershell
docker compose logs -f mysql
```

出现 `ready for connections` 即可，Ctrl+C 退出。

验证初始化结果（期望 2 个测点，unit 为 `℃` 和 `%`）：

```powershell
docker compose exec mysql mysql -uroot -p123456 -e "use donghuan; select id, point_code, unit from point;"
```

`seed.sql` 写入：测试用户 `id=1`、设备 `th-01`、测点 `temperature` / `humidity`、4 条采样值、1 条 ACTIVE 告警。
其中 ACTIVE 告警会在应用启动时回填进内存；只想测“新告警插入”时，先删掉它再启动。

EMQX 控制台：<http://localhost:18083>（`admin / public`）。

### 3. 修改配置

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
- 设备、测点、阈值、解析规则全部从数据库读取，不再使用 `devices.yml`
- 采样数据按 UTC 存储，查询时传 `...Z` 或带偏移的时间

启动前先确认账号密码和库名可用：

```powershell
docker compose exec mysql mysql -uroot -p123456 -e "use donghuan; select count(*) from point;"
```

### 4. 启动应用

```bash
mvn spring-boot:run
```

启动日志应能看到：设备配置加载、MQTT 连接成功、设备接入完成。

### 5. 启动模拟器

另开一个终端：

```bash
python simulator.py
# Windows 也可以用：py simulator.py
```

模拟器每秒向 `donghuan/th/01` 发一条包含 `temperature / humidity / ts` 的报文。

### 6. 验证

```bash
curl http://localhost:8080/api/devices
curl http://localhost:8080/api/snapshots
curl http://localhost:8080/api/points/th-01/temperature/latest
curl "http://localhost:8080/api/points/th-01/temperature/range?start=2026-11-06T00:00:00Z&end=2026-11-07T00:00:00Z"
curl http://localhost:8080/api/alarms/active
curl -X POST http://localhost:8080/api/alarms/1/confirm
```

`/api/snapshots` 返回内存最新值，`/api/points/.../latest` 查数据库，两者可对照验证 3 秒采样节流是否生效。

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/devices | 全部设备（读数据库） |
| GET | /api/devices/{deviceId} | 单个设备，不存在返回 404 |
| GET | /api/snapshots | 全部测点的内存最新快照 |
| GET | /api/points/{deviceId}/{pointId}/latest | 库中最新一条 OK 数据，无数据返回 404 |
| GET | /api/points/{deviceId}/{pointId}/range?start=&end= | 时间范围内的 OK 数据（ISO-8601） |
| PUT | /api/points/{deviceId}/{pointId}/threshold | 修改阈值，body: `{"min":18,"max":38}` |
| GET | /api/alarms/active | 全部 ACTIVE 告警 |
| GET | /api/alarms | 全部告警（含 RECOVERED） |
| POST | /api/alarms/{id}/confirm | 确认告警，返回影响行数（0 表示不存在或已恢复） |

## 配置说明

配置已全部库化，设备与测点由 `device`、`point` 两张表驱动：

| 表.字段 | 作用 |
|---|---|
| device.device_code | 设备标识，接口与 MQTT topic 使用 |
| device.connection_config | MQTT topic，如 `donghuan/th/01` |
| device.enabled | 停用后不加载、不接入 |
| point.point_code | 测点标识，如 `temperature` |
| point.address | 取值 JsonPath，如 `$.temperature` |
| point.time_address | 取样时间 JsonPath，如 `$.ts` |
| point.unit | 单位，如 `℃` |
| point.min_value / max_value | 告警阈值 |
| point.freq_seconds | 采样间隔字段（当前节流固定 3 秒，未使用该字段） |
| point.enabled | 停用后不加载映射、不参与告警 |

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
├── service/        告警收敛（内存 ACTIVE + 启动回填）
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

**导入 SQL 报 1064，或中文变成 `?`**
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
