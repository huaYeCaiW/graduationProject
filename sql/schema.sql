-- 农业大棚监测与合作社服务平台 建表脚本
-- 版本：v4.4  日期：2026-11-20
-- 变更：新增 tenant 表；user / device 增加 tenant_id（阶段 1 租户化）；
--       point.address 兼容 Modbus 寄存器串（阶段 7）；
--       申请单改为只记录种植户需求（大棚名、申请人、电话、测点类型与数量），
--       移除服务项 / 指标模板；审批通过后由开通任务按现场设备配置测点
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

-- 服务申请：种植户提交，一单一棚，只表达需求，不含任何技术配置
-- 状态：PENDING / APPROVED / REJECTED / INSTALLED
create table service_application (
                                     id bigint auto_increment primary key,
                                     application_no varchar(32) not null,
                                     tenant_id bigint not null,
                                     applicant_id bigint not null,
                                     applicant_name varchar(64) not null,
                                     greenhouse_name varchar(128) not null,
                                     phone varchar(32) not null,
                                     status varchar(16) not null default 'PENDING',
                                     reject_reason varchar(255) null,
                                     reviewed_by bigint null,
                                     reviewed_at datetime(3) null,
                                     confirmed_by bigint null,
                                     confirmed_at datetime(3) null,
                                     created_at datetime(3) not null default current_timestamp(3),
                                     updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
                                     unique key uk_application_no (application_no),
                                     key idx_application_tenant (tenant_id),
                                     key idx_application_applicant (applicant_id),
                                     key idx_application_status (status),
                                     constraint fk_application_tenant foreign key (tenant_id) references tenant (id),
                                     constraint fk_application_user foreign key (applicant_id) references user (id),
                                     constraint fk_application_reviewer foreign key (reviewed_by) references user (id),
                                     constraint fk_application_confirmer foreign key (confirmed_by) references user (id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- 申请需求项：种植户想装几路什么类型的测点
-- 具体设备与测点在运维现场安装后，通过设备/测点 CRUD 录入
create table application_point (
                                   id bigint auto_increment primary key,
                                   application_id bigint not null,
                                   point_type varchar(32) not null,
                                   point_count int not null,
                                   created_at datetime(3) not null default current_timestamp(3),
                                   key idx_application_point_application (application_id),
                                   constraint fk_application_point_application foreign key (application_id) references service_application (id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- 运维工单：种植户报修 → 运维接单 → 完工 → 种植户验收
-- 状态：PENDING / IN_PROGRESS / WAITING_ACCEPT / DONE / CANCELLED
-- 接单只记录归属，完工不限接单人；取消原因对管理员必填
create table work_order (
                            id bigint auto_increment primary key,
                            order_no varchar(32) not null,
                            tenant_id bigint not null,
                            applicant_id bigint not null,
                            applicant_name varchar(64) not null,
                            greenhouse_name varchar(128) not null,
                            phone varchar(32) not null,
                            description varchar(500) not null,
                            status varchar(16) not null default 'PENDING',
                            accepted_by bigint null,
                            accepted_at datetime(3) null,
                            completed_by bigint null,
                            completed_at datetime(3) null,
                            complete_note varchar(255) null,
                            confirmed_at datetime(3) null,
                            cancelled_by bigint null,
                            cancelled_at datetime(3) null,
                            cancel_reason varchar(255) null,
                            created_at datetime(3) not null default current_timestamp(3),
                            updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
                            unique key uk_work_order_no (order_no),
                            key idx_work_order_tenant (tenant_id),
                            key idx_work_order_applicant (applicant_id),
                            key idx_work_order_status (status),
                            constraint fk_work_order_tenant foreign key (tenant_id) references tenant (id),
                            constraint fk_work_order_user foreign key (applicant_id) references user (id),
                            constraint fk_work_order_acceptor foreign key (accepted_by) references user (id),
                            constraint fk_work_order_completer foreign key (completed_by) references user (id),
                            constraint fk_work_order_canceller foreign key (cancelled_by) references user (id)
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
