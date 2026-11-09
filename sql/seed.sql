-- 部署测试用种子数据（阶段 1 租户化后）
-- 执行前提：已执行 schema.sql
-- 可重复执行：固定主键 + INSERT IGNORE；但不会覆盖已存在的行，需重置请先清表
-- 时间统一按 UTC 写入，与 schema.sql 约定一致

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
    (1, 'admin',   '$2b$10$f/lvjN5Ef7IVgQMx5e.4EO/N4ZUJzvzyYWezRJmw9OKdH17uivm7q', 'admin',   null, 1),
    (2, 'ops',     '$2b$10$f/lvjN5Ef7IVgQMx5e.4EO/N4ZUJzvzyYWezRJmw9OKdH17uivm7q', 'ops',     2,    1),
    (3, 'farmer1', '$2b$10$f/lvjN5Ef7IVgQMx5e.4EO/N4ZUJzvzyYWezRJmw9OKdH17uivm7q', 'farmer',  3,    1),
    (4, 'farmer2', '$2b$10$f/lvjN5Ef7IVgQMx5e.4EO/N4ZUJzvzyYWezRJmw9OKdH17uivm7q', 'farmer',  4,    1);

-- 3) 设备：th-01 属于 farm-a（有模拟器数据），th-02 属于 farm-b（用于跨租户越权测试）
insert ignore into device (id, device_code, tenant_id, name, protocol, connection_config, enabled)
values
    (1, 'th-01', 3, '一号大棚温湿度传感器', 'mqtt', 'donghuan/th/01', 1),                                                                                                   (2, 'th-02', 4, '二号大棚温湿度传感器', 'mqtt', 'donghuan/th/02', 1);

-- 4) 测点：point_code 与模拟器报文一致，阈值用于告警验证
insert ignore into point
(id, device_id, point_code, name, address, time_address, data_type, unit, freq_seconds, min_value, max_value, enabled)
values
    (1, 1, 'temperature', '温度', '$.temperature', '$.ts', 'float', '℃',  60, 18.000, 38.000, 1),
    (2, 1, 'humidity',    '湿度', '$.humidity',    '$.ts', 'float', '%',   60, 30.000, 70.000, 1),
    (3, 2, 'temperature', '温度', '$.temperature', '$.ts', 'float', '℃',  60, 18.000, 38.000, 1),
    (4, 2, 'humidity',    '湿度', '$.humidity',    '$.ts', 'float', '%',   60, 30.000, 70.000, 1);

-- 5) 采样数据：让 /api/points/{device}/{point}/latest 与 /range 不依赖 MQTT 就能出结果
insert ignore into point_value (id, point_id, value, sample_at, sys_ts, quality)
values
    (1, 1, 23.500, utc_timestamp(3) - interval 10 minute, utc_timestamp(3) - interval 10 minute, 'OK'),
    (2, 2, 45.000, utc_timestamp(3) - interval 10 minute, utc_timestamp(3) - interval 10 minute, 'OK'),
    (3, 1, 39.500, utc_timestamp(3) - interval  3 minute, utc_timestamp(3) - interval  3 minute, 'OK'),
    (4, 2, 61.000, utc_timestamp(3) - interval  3 minute, utc_timestamp(3) - interval  3 minute, 'OK');

-- 6) 一条 ACTIVE 告警：应用启动时会由 findActive 回填到内存，
--    随后若温度恢复到 18~38，会走 markRecovered；可用于验证回填与收敛
--    只想测“新告警插入”时，把这行删掉再启动
insert ignore into alarm
(id, point_id, value, threshold, limit_type, status, sample_at, created_at)
values
    (1, 1, 39.500, 38.000, 'MAX', 'ACTIVE', utc_timestamp(3) - interval 3 minute, utc_timestamp(3) - interval 3 minute);

-- 7) 自检：应返回 4 租户 / 4 用户 / 2 设备 / 4 测点 / 4 采样 / 1 告警
-- select
--   (select count(*) from tenant) as tenants,
--   (select count(*) from user) as users,
--   (select count(*) from device) as devices,
--   (select count(*) from point) as points,
--   (select count(*) from point_value) as values_,
--   (select count(*) from alarm) as alarms;
