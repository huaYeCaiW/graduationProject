# 农业大棚监测与合作社服务平台

面向合作社的大棚环境监测与服务管理平台：同时接入 MQTT 与 Modbus TCP 设备，统一完成解析、清洗、告警收敛与入库，并提供平台管理员、运维、种植户三种角色的 Web 界面与工单、服务申请流程。

- **双协议接入**：`ProtocolAdapter` 策略接口 + 协议注册表，MQTT（Paho/EMQX）与 Modbus TCP（手写 MBAP/PDU）共用同一条解析、清洗、告警、存储链路。
- **流式处理**：Reactor `Sinks.Many` 多播热流，`share()` 后分三路订阅快照、告警、存储；入库按测点 `freq_seconds` 节流。
- **告警收敛**：按测点主键维护内存 ACTIVE 状态，首次越限插入、持续越限只更新、恢复置 RECOVERED，启动时从库回填。
- **多租户与权限**：单库多租户，租户过滤下沉 Store 层；Session + BCrypt，写接口按角色鉴权。
- **业务闭环**：服务申请（待审批 → 通过/驳回 → 已安装）与运维工单（待受理 → 处理中 → 待验收 → 完成/取消）均为条件更新状态机。

技术栈：Java 21 / Spring Boot 4.0.8 / Spring MVC / Reactor / JdbcTemplate / MySQL 8.4 / MQTT（Paho + EMQX 5.8）/ Modbus TCP / Session + BCrypt / Docker Compose

## 环境

- JDK 21
- Maven 3.9+
- Docker Desktop（MySQL、EMQX、Modbus 模拟器）
- （可选）Python 3.10+，用于跑 MQTT / Modbus 模拟器

## 快速开始

### 方式一：Docker Compose 全量启动

在 Git Bash 或 WSL 中执行（脚本是 bash）：

```bash
cd donghuan
bash deploy.sh
```

脚本会生成带随机密码的 `.env`，依次启动 mysql、emqx、modbus，配置 EMQX 账号认证，再启动 app 并等待健康检查。

Windows PowerShell 没有 bash，可以手工执行等价步骤：

```powershell
cd donghuan
Copy-Item .env.example .env      # 然后将其中 change-me 改成自己的密码
docker compose up -d --build mysql emqx modbus
# 打开 http://127.0.0.1:18083 ，在「访问控制 → 认证」创建内置数据库认证并添加账号
docker compose up -d --build app
```

启动完成后访问 <http://localhost:8080>。四个容器的职责：

| 容器 | 作用 | 端口 |
| --- | --- | --- |
| `dh-app` | 应用 | `8080` |
| `dh-mysql` | MySQL 8.4 | `127.0.0.1:3306` |
| `dh-emqx` | MQTT Broker | `1883`，控制台 `127.0.0.1:18083` |
| `dh-modbus` | Modbus TCP 从站模拟器 | 仅 compose 网络内 `5020` |

密码都在 `.env` 里：`MYSQL_ROOT_PASSWORD`、`EMQX_DASHBOARD_PASSWORD`、`MQTT_USERNAME` / `MQTT_PASSWORD`。`.env` 已被 `.gitignore` 忽略。

### 方式二：本地开发（应用跑在宿主机）

只启动依赖，**不要**执行不带服务名的 `docker compose up -d`，否则 app 容器会占用 8080：

```powershell
cd donghuan
docker compose up -d mysql emqx
mvn spring-boot:run
```

另开终端跑数据模拟器：

```powershell
py simulator.py              # 只跑 MQTT 设备
py simulator.py --all --api http://localhost:8080 --username admin --password password
```

需要 Modbus 数据时，再单独启动从站：

```powershell
pip install pymodbus
python scripts/modbus_simulator.py --port 5020
```

种子里的 `mb-01` 连接串是 `127.0.0.1:5020:1`，正好对上。

### 建库与重置

数据卷为空时，MySQL 首次启动会自动执行 `sql/schema.sql`、`sql/seed.sql`。确认：

```powershell
docker compose exec mysql mysql -uroot -p123456 -e "use donghuan; select device_code, protocol from device;"
```

期望 3 台设备：`th-01`、`th-02`（MQTT）和 `mb-01`（Modbus）。

重置全部数据：

```powershell
docker compose down -v
docker compose up -d
```

## 登录与角色

种子账号密码统一为 `password`：

| 账号 | 角色 | 所属租户 | 数据范围 |
| --- | --- | --- | --- |
| `admin` | ADMIN | 平台 | 全部租户 |
| `ops` | OPS | 绿源合作社 | 本租户 |
| `farmer1` | FARMER | 一号大棚 | 本租户 |
| `farmer2` | FARMER | 二号大棚 | 本租户 |

角色能力：

- 设备、测点的增删改：ADMIN
- 阈值修改：任意登录角色
- 告警确认：OPS / ADMIN
- 服务申请提交：FARMER；审批、驳回：ADMIN；安装确认：OPS / ADMIN
- 工单提交：FARMER；受理、完成：OPS；验收：FARMER；取消：FARMER 只能取消待受理，ADMIN 可取消待受理与处理中

除 `/api/auth/**` 外，所有 `/api/**` 都需要登录。浏览器直接访问页面即可，脚本调用要先登录拿 Session Cookie：

```bash
curl -c cookie.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"password\"}"

curl -b cookie.txt http://localhost:8080/api/devices
curl -b cookie.txt http://localhost:8080/api/snapshots
curl -b cookie.txt http://localhost:8080/api/alarms/active
```

## 接口

### 认证

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 登录，创建 Session（会 `changeSessionId()` 防会话固定） |
| GET | `/api/auth/me` | 当前登录用户 |
| POST | `/api/auth/logout` | 退出登录 |

### 设备

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/devices` | 设备列表（按租户过滤） |
| GET | `/api/devices/disabled` | 已逻辑删除的设备 |
| GET | `/api/devices/{deviceCode}` | 单个设备，不存在 404 |
| POST | `/api/devices` | 新增设备（ADMIN），编码重复 409 |
| PUT | `/api/devices/{deviceCode}` | 修改设备（ADMIN） |
| DELETE | `/api/devices/{deviceCode}` | 逻辑删除（ADMIN），历史数据保留 |

### 测点与数据

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/points/{deviceCode}/{pointCode}/latest` | 最新一条 OK 数据，无数据 404 |
| GET | `/api/points/{deviceCode}/{pointCode}/range?start=&end=` | 时间范围 OK 数据（ISO-8601） |
| POST | `/api/points/{deviceCode}` | 新增测点（ADMIN） |
| PUT | `/api/points/{deviceCode}/{pointCode}` | 修改测点（ADMIN），修改后热生效 |
| DELETE | `/api/points/{deviceCode}/{pointCode}` | 逻辑删除测点（ADMIN） |
| PUT | `/api/points/{deviceCode}/{pointCode}/threshold` | 修改告警阈值，body `{"min":18,"max":38}` |
| PUT | `/api/points/{deviceCode}/{pointCode}/range` | 修改物理量程，body `{"min":-40,"max":80}` |
| GET | `/api/snapshots` | 内存最新快照 |
| GET | `/api/snapshots/stream` | SSE 推送：连接时全量，之后约 500ms 增量 |

### 告警

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/alarms/active` | ACTIVE 告警 |
| GET | `/api/alarms` | 全部告警（含 RECOVERED） |
| POST | `/api/alarms/{id}/confirm` | 确认告警，影响 0 行返回 404 |

### 运维工单

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/work-orders` | 提交工单（FARMER） |
| GET | `/api/work-orders/mine` | 我的工单 |
| GET | `/api/work-orders` | 工单列表（OPS / ADMIN） |
| GET | `/api/work-orders/{id}` | 工单详情，越权 403 |
| POST | `/api/work-orders/{id}/accept` | 受理（OPS），状态不符 409 |
| POST | `/api/work-orders/{id}/complete` | 处理完成（OPS），状态不符 409 |
| POST | `/api/work-orders/{id}/confirm` | 验收（FARMER） |
| POST | `/api/work-orders/{id}/cancel` | 取消，按角色限制可取消的状态 |

### 服务申请

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/service-applications` | 种植户提交申请（棚名、联系人、测点需求） |
| GET | `/api/service-applications/mine` | 我的申请 |
| GET | `/api/service-applications` | 申请列表（OPS / ADMIN） |
| GET | `/api/service-applications/{id}` | 详情，非本人 403 |
| POST | `/api/service-applications/{id}/approve` | 通过（ADMIN） |
| POST | `/api/service-applications/{id}/reject` | 驳回（ADMIN），理由必填 |
| POST | `/api/service-applications/{id}/confirm` | 安装确认（OPS / ADMIN） |

## 配置说明

设备与测点全部库化，由 `device`、`point` 两张表驱动，修改后无需改代码。

| 表.字段 | 作用 |
| --- | --- |
| `device.device_code` | 设备标识，接口路径与 MQTT 主题使用 |
| `device.protocol` | `mqtt` 或 `modbus` |
| `device.connection_config` | MQTT 为主题（如 `donghuan/th/01`）；Modbus 为 `host:port:unitId` |
| `device.enabled` | 停用后不加载、不接入 |
| `point.point_code` | 测点标识，如 `temperature` |
| `point.address` | MQTT 为 JsonPath（`$.temperature`）；Modbus 为 `寄存器:地址:类型[:缩放]`（`holding:0:float32`） |
| `point.time_address` | 采样时间 JsonPath（`$.ts`）；为空时用接收时间 |
| `point.unit` | 单位，如 `℃` |
| `point.min_value` / `max_value` | 告警阈值 |
| `point.range_min` / `range_max` | 物理量程，越界由 `DataCleaner` 判 BAD；为空不校验 |
| `point.freq_seconds` | 采样间隔，入库与 Modbus 轮询按此执行，未配置默认 3 秒 |
| `point.enabled` | 停用后不加载映射、不参与告警 |

Modbus 支持的寄存器类型为 `coil` / `discrete` / `holding` / `input`，数据类型支持 `bool`、`uint16`、`int16`、`uint32`、`int32`、`float32`。报文缺 `ts` 时用服务器接收时间兜底。

## 项目结构

```
src/main/java/com/donghuan
├── acquire/        启动编排，按库中启用设备逐台接入
├── adapter/        ProtocolAdapter 接口
│   ├── mqtt/       Paho 订阅实现
│   └── modbus/     手写 MBAP/PDU 主站、轮询器
├── common/         设备配置、鉴权常量、文本校验
├── model/          PointValue / AlarmValue / 业务命令与视图
├── parser/         MessageParser 与 JsonPath 测点抽取
├── pipeline/       单位换算、量程校验、去重、阈值判定
├── service/        告警收敛、设备/测点、工单、服务申请
├── store/          Store 接口与内存快照；jdbc/ 为 JdbcTemplate 实现
├── stream/         Reactor 热流：快照 / 告警 / 存储三路订阅 + 按测点周期节流
└── web/            拦截器、全局异常、REST Controller
src/main/resources
├── application.properties
├── static/         登录、监控、管理、运维、种植户页面与前端脚本
└── ...
sql/                schema.sql / seed.sql / docker-init.sql
scripts/            EMQX 认证脚本、Modbus 模拟器、部署辅助
```

## 常见问题

**已有数据卷，初始化脚本没执行**
初始化脚本只在 `/var/lib/mysql` 为空时执行。可以 `docker compose down -v` 删卷重建，或手工导入：

```powershell
cmd /c "docker compose exec -T mysql mysql -uroot -p123456 < sql\schema.sql"
cmd /c "docker compose exec -T mysql mysql -uroot -p123456 < sql\seed.sql"
```

`schema.sql` 没有 `if not exists`，不能对已建表的库重复执行。

**导入 SQL 报 1064 或中文变成 `?`**
PowerShell 的 `<` 重定向默认按 ANSI 编码，会破坏中文和 `℃`，请用上面的 `cmd /c` 写法。

**8080 被占用**
先确认是不是 compose 里的 app 容器：

```powershell
docker compose ps
docker compose stop app
```

**数据库密码不对**
compose 的默认值 `123456` 只在没有 `.env` 时生效。跑过 `deploy.sh` 的目录里密码是随机生成的，用 `type .env` 查看，或手工执行 `sql` 时用它。

**MQTT 连不上**
Docker 全量部署时 EMQX 已开启账号认证，模拟器要带 `MQTT_USERNAME` / `MQTT_PASSWORD`。本地只起 `mysql emqx` 时默认允许匿名，可以先不填账号验证链路。

**端口冲突**
`3306` / `1883` / `8083` / `18083` / `8080` 被占用时，改 `docker-compose.yml` 的端口映射，同时同步 `application.properties` 里的数据库端口或 `mqtt.broker`。

**不想用 Docker 跑 MySQL**
可以用本机 MySQL 8，手工执行 `sql/schema.sql` 和 `sql/seed.sql`，再改 `application.properties` 的连接信息；EMQX 仍可用 compose 单独启动。

## 已知限制

- 单机单实例：快照、活跃告警、去重状态都在 JVM 内存，重启后快照与去重状态丢失，ACTIVE 告警从库回填。
- 设备断线不会产生“离线告警”，告警恢复依赖后续正常数据。
- `latest` / `range` 只返回 `quality=OK` 的数据，BAD 数据会入库但对查询不可见。
- 物理量程只能新增/修改，不能通过接口清空（不传表示沿用旧值）。
- 演示环境使用种子账号与 HTTP 明文，未配置 HTTPS 与安全响应头，不适合直接作为生产环境。
