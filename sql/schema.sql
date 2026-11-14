-- 农业大棚监测与合作社服务平台 建表脚本
-- 版本：v4.0  日期：2026-11-14
-- 变更：新增 tenant 表；user / device 增加 tenant_id（阶段 1 租户化）；
--       point.address 兼容 Modbus 寄存器串（阶段 7）；
--       新增服务项、指标模板、服务申请、开通任务，device 增加 application_id（阶段 8）
-- 注意：MySQL 服务器时区为 UTC，sample_at / created_at / sys_ts 均以 UTC 存储，展示层需 +8。

-- 显式声明客户端字符集，避免容器初始化时按 latin1 解释 UTF-8 脚本导致双重编码
set names utf8mb4;

create database if not exists donghuan default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
use donghuan;

-- 租户表：种植户 / 合作社 / 平台各为一个租户，数据隔离靠各表的 tenant_id
create table tenant (
                        id bigint auto_increment primary key,
                        code varchar(64) not null,
                        name varchar(128) not null,
                        contact varchar(64) null,
                        phone varchar(32) null,
                        status varchar(16) not null default 'ACTIVE',
                        created_at datetime(3) not null default current_timestamp(3),
                        unique key uk_tenant_code (code)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- 用户表：审计字段 confirmed_by / maintained_by 的外键目标，因此只能逻辑删除
-- tenant_id 为 null 表示平台账号（可看全部租户数据）
create table user (
                      id bigint auto_increment primary key,
                      username varchar(64) not null,
                      password_hash varchar(100) not null,
                      role varchar(32) not null,
                      tenant_id bigint null,
                      enabled tinyint(1) not null default 1,
                      created_at datetime(3) not null default current_timestamp(3),
                      unique key uk_username (username),
                      key idx_user_tenant (tenant_id),
                      constraint fk_user_tenant foreign key (tenant_id) references tenant (id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- 设备表（大棚）
-- tenant_id 为 null 表示尚未分配给任何租户，查询接口不应返回给租户用户
create table device (
                        id bigint auto_increment primary key,
                        device_code varchar(64) not null,
                        tenant_id bigint null,
                        application_id bigint null,
                        name varchar(128) not null,
                        protocol varchar(32) not null,
                        connection_config varchar(1024) null,
                        enabled tinyint(1) not null default 1,
                        last_seen_at datetime(3) null,
                        created_at datetime(3) not null default current_timestamp(3),
                        updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
                        unique key uk_device_code (device_code),
                        key idx_device_tenant (tenant_id),
                        key idx_device_application (application_id),
                        constraint fk_device_tenant foreign key (tenant_id) references tenant (id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- 测点表：同一设备下 point_code 唯一
-- address 双语义：MQTT 存 JSON 路径（如 $.temperature）
--                 Modbus 存 寄存器类型:地址:数据类型[:缩放]（如 holding:0:float32:0.1）
-- time_address 仅 MQTT 使用；Modbus 无设备时间戳，采样时间取采集时间
-- data_type 对 Modbus 作为 address 第 3 段为空时的兜底
-- min_value/max_value 为告警阈值；range_min/range_max 为物理量程，仅供清洗层判定 BAD
create table point (
                       id bigint auto_increment primary key,
                       device_id bigint not null,
                       point_code varchar(64) not null,
                       name varchar(128) not null,
                       address varchar(1024) null,
                       time_address varchar(255) null,
                       data_type varchar(16) not null,
                       unit varchar(16) null,
                       freq_seconds int null,
                       min_value decimal(10,3) null,
                       max_value decimal(10,3) null,
                       range_min decimal(10,3) null,
                       range_max decimal(10,3) null,
                       enabled tinyint(1) not null default 1,
                       created_at datetime(3) not null default current_timestamp(3),
                       updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
                       unique key uk_device_point (device_id, point_code),
                       key idx_device (device_id),
                       constraint fk_point_device foreign key (device_id) references device (id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- 服务项：合作社对外提供的监测服务套餐
-- max_greenhouses 为一次申请最多开通的大棚数，max_points_per_greenhouse 为单棚测点上限
create table service_item (
                              id bigint auto_increment primary key,
                              code varchar(64) not null,
                              name varchar(128) not null,
                              description varchar(512) null,
                              max_greenhouses int not null default 1,
                              max_points_per_greenhouse int not null default 4,
                              enabled tinyint(1) not null default 1,
                              created_at datetime(3) not null default current_timestamp(3),
                              updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
                              unique key uk_service_item_code (code)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- 指标模板：开通时按这里的编码/名称/单位/默认阈值生成测点骨架
create table service_metric (
                                id bigint auto_increment primary key,
                                service_item_id bigint not null,
                                metric_code varchar(64) not null,
                                name varchar(128) not null,
                                unit varchar(16) null,
                                data_type varchar(16) not null default 'float',
                                default_min decimal(10,3) null,
                                default_max decimal(10,3) null,
                                range_min decimal(10,3) null,
                                range_max decimal(10,3) null,
                                freq_seconds int null,
                                sort_no int not null default 0,
                                unique key uk_service_metric (service_item_id, metric_code),
                                constraint fk_metric_item foreign key (service_item_id) references service_item (id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- 服务申请：种植户提交，管理员审批；驳回必须写 reject_reason
-- 状态：PENDING / APPROVED / REJECTED / PROVISIONED
create table service_application (
                                     id bigint auto_increment primary key,
                                     application_no varchar(32) not null,
                                     service_item_id bigint not null,
                                     tenant_id bigint not null,
                                     applicant_id bigint not null,
                                     greenhouse_count int not null,
                                     contact varchar(64) not null,
                                     phone varchar(32) null,
                                     status varchar(16) not null default 'PENDING',
                                     reject_reason varchar(255) null,
                                     reviewed_by bigint null,
                                     reviewed_at datetime(3) null,
                                     created_at datetime(3) not null default current_timestamp(3),
                                     updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
                                     unique key uk_application_no (application_no),
                                     key idx_application_tenant (tenant_id),
                                     key idx_application_applicant (applicant_id),
                                     key idx_application_status (status),
                                     constraint fk_application_item foreign key (service_item_id) references service_item (id),
                                     constraint fk_application_tenant foreign key (tenant_id) references tenant (id),
                                     constraint fk_application_user foreign key (applicant_id) references user (id),
                                     constraint fk_application_reviewer foreign key (reviewed_by) references user (id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- 开通任务：审批通过自动生成，运维执行开通
-- 状态：PENDING / DONE（RUNNING 只存在于事务内，失败会回滚）
create table provision_task (
                                id bigint auto_increment primary key,
                                application_id bigint not null,
                                status varchar(16) not null default 'PENDING',
                                assigned_to bigint null,
                                started_at datetime(3) null,
                                finished_at datetime(3) null,
                                remark varchar(255) null,
                                created_at datetime(3) not null default current_timestamp(3),
                                unique key uk_provision_application (application_id),
                                constraint fk_provision_application foreign key (application_id) references service_application (id),
                                constraint fk_provision_user foreign key (assigned_to) references user (id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- 开通任务设备快照：任务详情按快照展示，设备后续复用不影响历史记录
create table provision_task_device (
                                       id bigint auto_increment primary key,
                                       task_id bigint not null,
                                       device_id bigint not null,
                                       device_code varchar(64) not null,
                                       name varchar(128) not null,
                                       protocol varchar(32) not null,
                                       point_count int not null default 0,
                                       created_at datetime(3) not null default current_timestamp(3),
                                       unique key uk_task_device (task_id, device_id),
                                       key idx_task_device_task (task_id),
                                       constraint fk_task_device_task foreign key (task_id) references provision_task (id),
                                       constraint fk_task_device_device foreign key (device_id) references device (id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- 采样数据表：sample_at 为设备采样时间，sys_ts 为落库时间
-- quality 由清洗层显式写入（OK / BAD），默认值仅兜底
create table point_value (
                             id bigint auto_increment primary key,
                             point_id bigint not null,
                             value decimal(10,3) not null,
                             sample_at datetime(3) not null,
                             sys_ts datetime(3) not null default current_timestamp(3),
                             quality varchar(16) not null default 'OK',
                             key idx_point_sample (point_id, sample_at),
                             constraint fk_value_point foreign key (point_id) references point (id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- 告警表：status 仅 ACTIVE / RECOVERED 双态，确认通过 confirmed_by / confirmed_at 标记
-- sample_at 为首次越限时间，value 为最近一次越限值，created_at 为落库时间
create table alarm (
                       id bigint auto_increment primary key,
                       point_id bigint not null,
                       value decimal(10,3) not null,
                       threshold decimal(10,3) not null,
                       limit_type varchar(8) not null,
                       status varchar(16) not null,
                       sample_at datetime(3) not null,
                       created_at datetime(3) not null default current_timestamp(3),
                       confirmed_by bigint null,
                       confirmed_at datetime(3) null,
                       recovered_at datetime(3) null,
                       maintained_by bigint null,
                       key idx_point_status (point_id, status),
                       key idx_status_created (status, created_at),
                       constraint fk_alarm_point foreign key (point_id) references point (id),
                       constraint fk_alarm_confirm foreign key (confirmed_by) references user (id),
                       constraint fk_alarm_maintain foreign key (maintained_by) references user (id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
