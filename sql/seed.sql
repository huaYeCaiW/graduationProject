-- 部署测试用种子数据（对应 src/main/resources/devices.yml 中的 th-01）
-- 执行前提：已执行 schema.sql
-- 可重复执行：固定主键 + INSERT IGNORE；但不会覆盖已存在的行，需重置请先清表
-- 时间统一按 UTC 写入，与 schema.sql 约定一致

use donghuan;

-- 1) 测试用户：id=1，对应 AlarmController 里的 TEST_USER_ID
--    password_hash 为 bcrypt("password") 的已知测试值，阶段 5 接入登录后才用得到
insert ignore into user (id, username, password_hash, role, enabled) values
(1, 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin', 1);

-- 2) 设备：device_code 必须与 devices.yml 的 id 一致（th-01），
--    否则 PointRefCache / ThresholdLoader / 清洗都会匹配不上
insert ignore into device (id, device_code, name, protocol, connection_config, enabled) values
(1, 'th-01', '机房温湿度传感器', 'mqtt', 'donghuan/th/01', 1);

-- 3) 测点：point_code 必须与 devices.yml 的 pointId 一致
--    阈值与 yml 保持一致（温度 18~38，湿度 30~70），便于验证库覆盖 yml
insert ignore into point
(id, device_id, point_code, name, address, time_address, data_type, unit, freq_seconds, min_value, max_value, enabled) values
(1, 1, 'temperature', '温度', '$.temperature', '$.ts', 'float', '℃',  60, 18.000, 38.000, 1),
(2, 1, 'humidity',    '湿度', '$.humidity', '$.ts', 'float', '%',   60, 30.000, 70.000, 1);

-- 4) 采样数据：让 /api/points/{device}/{point}/latest 与 /range 不依赖 MQTT 就能出结果
insert ignore into point_value (id, point_id, value, sample_at, sys_ts, quality) values
(1, 1, 23.500, utc_timestamp(3) - interval 10 minute, utc_timestamp(3) - interval 10 minute, 'OK'),
(2, 2, 45.000, utc_timestamp(3) - interval 10 minute, utc_timestamp(3) - interval 10 minute, 'OK'),
(3, 1, 39.500, utc_timestamp(3) - interval  3 minute, utc_timestamp(3) - interval  3 minute, 'OK'),
(4, 2, 61.000, utc_timestamp(3) - interval  3 minute, utc_timestamp(3) - interval  3 minute, 'OK');

-- 5) 一条 ACTIVE 告警：应用启动时会由 findActive 回填到内存，
--    随后若温度恢复到 18~38，会走 markRecovered；可用于验证回填与收敛
--    只想测“新告警插入”时，把这行删掉再启动
insert ignore into alarm
(id, point_id, value, threshold, limit_type, status, sample_at, created_at) values
(1, 1, 39.500, 38.000, 'MAX', 'ACTIVE', utc_timestamp(3) - interval 3 minute, utc_timestamp(3) - interval 3 minute);

-- 6) 自检：应返回 1 用户 / 1 设备 / 2 测点 / 4 采样 / 1 告警
-- select
--   (select count(*) from user) as users,
--   (select count(*) from device) as devices,
--   (select count(*) from point) as points,
--   (select count(*) from point_value) as values_,
--   (select count(*) from alarm) as alarms;
