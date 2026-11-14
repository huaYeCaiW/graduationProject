-- 部署测试用种子数据（阶段 1 租户化 + 阶段 8 服务目录）
-- 执行前提：已执行 schema.sql
-- 可重复执行：固定主键 + INSERT IGNORE；但不会覆盖已存在的行，需重置请先清表
-- 时间统一按 UTC 写入，与 schema.sql 约定一致

-- 显式声明客户端字符集，避免容器初始化时按 latin1 解释 UTF-8 脚本导致双重编码
set names utf8mb4;

use donghuan;

-- 1) 租户：1 平台 / 2 合作社 / 3、4 两个种植户
insert ignore into tenant (id, code, name, contact, phone, status)
values
    (1, 'platform', '平台运营方', '平台管理员', '13800000000', 'ACTIVE'),
    (2, 'coop',     '绿源合作社', '李运维',     '13800000001', 'ACTIVE'),
    (3, 'farm-a',   '一号大棚',   '张农',       '13800000002', 'ACTIVE'),
    (4, 'farm-b',   '二号大棚',   '王农',       '13800000003', 'ACTIVE');

-- 2) 用户：password_hash 均为 bcrypt("password") 的已校验测试值
--    tenant_id 为 null 表示平台账号，可看全部租户数据
insert ignore into user (id, username, password_hash, role, tenant_id, enabled)
values
    (1, 'admin',   '$2b$10$f/lvjN5Ef7IVgQMx5e.4EO/N4ZUJzvzyYWezRJmw9OKdH17uivm7q', 'ADMIN',   null, 1),
    (2, 'ops',     '$2b$10$f/lvjN5Ef7IVgQMx5e.4EO/N4ZUJzvzyYWezRJmw9OKdH17uivm7q', 'OPS',     2,    1),
    (3, 'farmer1', '$2b$10$f/lvjN5Ef7IVgQMx5e.4EO/N4ZUJzvzyYWezRJmw9OKdH17uivm7q', 'FARMER',  3,    1),
    (4, 'farmer2', '$2b$10$f/lvjN5Ef7IVgQMx5e.4EO/N4ZUJzvzyYWezRJmw9OKdH17uivm7q', 'FARMER',  4,    1);

-- 3) 设备：th-01 / th-02 为 MQTT 设备（用于跨租户对照），mb-01 为 Modbus TCP 设备
--    Modbus 的 connection_config = host:port:unitId
--    若应用跑在容器内、模拟器跑在宿主机，把 127.0.0.1 换成 host.docker.internal
insert ignore into device (id, device_code, tenant_id, name, protocol, connection_config, enabled)
values
    (1, 'th-01', 3, '一号大棚温湿度传感器', 'mqtt', 'donghuan/th/01', 1),
    (2, 'th-02', 4, '二号大棚温湿度传感器', 'mqtt', 'donghuan/th/02', 1),
    (3, 'mb-01', 3, '一号大棚 Modbus 环境控制器', 'modbus', '127.0.0.1:5020:1', 1);

-- 4) 测点：MQTT 的 address 为 JSON 路径；Modbus 的 address 为 寄存器类型:地址:数据类型[:缩放]
--    Modbus 的 time_address 为空，采样时间取采集时间
--    min_value/max_value 为告警阈值；range_min/range_max 为物理量程，为空表示不校验
insert ignore into point
(id, device_id, point_code, name, address, time_address, data_type, unit, freq_seconds, min_value, max_value, range_min, range_max, enabled)
values
    (1, 1, 'temperature', '温度', '$.temperature', '$.ts', 'float', '℃',  60, 18.000, 38.000, -40.000, 80.000, 1),
    (2, 1, 'humidity',    '湿度', '$.humidity',    '$.ts', 'float', '%',   60, 30.000, 70.000,   0.000, 100.000, 1),
    (3, 2, 'temperature', '温度', '$.temperature', '$.ts', 'float', '℃',  60, 18.000, 38.000, -40.000, 80.000, 1),
    (4, 2, 'humidity',    '湿度', '$.humidity',    '$.ts', 'float', '%',   60, 30.000, 70.000,   0.000, 100.000, 1),
    (5, 3, 'temperature', '空气温度', 'holding:0:float32',    null, 'float32', '℃', 5, 15.000, 40.000, -40.000, 80.000, 1),
    (6, 3, 'humidity',    '空气湿度', 'holding:2:float32',    null, 'float32', '%',  5, 30.000, 85.000,   0.000, 100.000, 1),
    (7, 3, 'soil',        '土壤湿度', 'holding:4:uint16:0.1', null, 'uint16',  '%',  5, 10.000, 80.000,   0.000, 100.000, 1),
    (8, 3, 'light',       '光照强度', 'holding:5:uint16',     null, 'uint16',  'lx', 5, 0.000, 20000.000,  0.000, 200000.000, 1),
    (9, 3, 'door',        '卷帘状态', 'discrete:0:bool',      null, 'bool',    null, 5, null, null, null, null, 1);

-- 5) 采样数据：让 /api/points/{device}/{point}/latest 与 /range 不依赖 MQTT 就能出结果
--    th-01（point_id 1、2）与 th-02（point_id 3、4）各两条，便于做跨租户对照
--    注意：固定主键只适用于空库首次初始化；若库中已有 MQTT 采集数据占用了 id 5~8，
--    本节会被 insert ignore 跳过，此时请去掉 id 列单独执行本节
insert ignore into point_value (id, point_id, value, sample_at, sys_ts, quality)
values
    (1, 1, 23.500, utc_timestamp(3) - interval 10 minute, utc_timestamp(3) - interval 10 minute, 'OK'),
    (2, 2, 45.000, utc_timestamp(3) - interval 10 minute, utc_timestamp(3) - interval 10 minute, 'OK'),
    (3, 1, 39.500, utc_timestamp(3) - interval  3 minute, utc_timestamp(3) - interval  3 minute, 'OK'),
    (4, 2, 61.000, utc_timestamp(3) - interval  3 minute, utc_timestamp(3) - interval  3 minute, 'OK'),
    (5, 3, 24.000, utc_timestamp(3) - interval 10 minute, utc_timestamp(3) - interval 10 minute, 'OK'),
    (6, 4, 52.000, utc_timestamp(3) - interval 10 minute, utc_timestamp(3) - interval 10 minute, 'OK'),
    (7, 3, 33.000, utc_timestamp(3) - interval  3 minute, utc_timestamp(3) - interval  3 minute, 'OK'),
    (8, 4, 58.000, utc_timestamp(3) - interval  3 minute, utc_timestamp(3) - interval  3 minute, 'OK');

-- 6) 一条 ACTIVE 告警：应用启动时会由 findAllActive 回填到内存，
--    随后若温度恢复到 18~38，会走 markRecovered；可用于验证回填与收敛
--    只想测“新告警插入”时，把这行删掉再启动
insert ignore into alarm
(id, point_id, value, threshold, limit_type, status, sample_at, created_at)
values
    (1, 1, 39.500, 38.000, 'MAX', 'ACTIVE', utc_timestamp(3) - interval 3 minute, utc_timestamp(3) - interval 3 minute);

-- 7) 服务目录：一个标准大棚监测服务 + 四项指标模板
--    开通任务按 service_metric 生成测点骨架，默认阈值/量程/周期可在管理端修改
insert ignore into service_item
(id, code, name, description, max_greenhouses, max_points_per_greenhouse, enabled)
values
    (1, 'greenhouse-basic', '标准大棚监测服务', '温度、空气湿度、土壤湿度、光照四项基础监测', 2, 6, 1);

insert ignore into service_metric
(id, service_item_id, metric_code, name, unit, data_type, default_min, default_max, range_min, range_max, freq_seconds, sort_no)
values
    (1, 1, 'temperature', '温度',     '℃', 'double', 18, 38,    -40, 80,     5, 1),
    (2, 1, 'humidity',    '空气湿度', '%',  'double', 30, 85,      0, 100,    5, 2),
    (3, 1, 'soil',        '土壤湿度', '%',  'double', 10, 80,      0, 100,    5, 3),
    (4, 1, 'light',       '光照',     'lx', 'double',  0, 50000,   0, 200000, 5, 4);

-- 8) 自检：应返回 4 租户 / 4 用户 / 3 设备 / 9 测点 / 8 采样 / 1 告警 / 1 服务项 / 4 指标模板
-- select
--   (select count(*) from tenant) as tenants,
--   (select count(*) from user) as users,
--   (select count(*) from device) as devices,
--   (select count(*) from point) as points,
--   (select count(*) from point_value) as values_,
--   (select count(*) from alarm) as alarms,
--   (select count(*) from service_item) as services,
--   (select count(*) from service_metric) as metrics;
