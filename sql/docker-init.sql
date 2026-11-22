-- 仅在 Docker Compose 环境中执行：应用容器通过服务名访问 Modbus 模拟器
-- 本地 IDE 运行时仍使用 seed.sql 里的 127.0.0.1:5020:1
update device
set connection_config = 'modbus:5020:1'
where device_code = 'mb-01';
