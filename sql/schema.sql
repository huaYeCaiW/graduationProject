-- 机房动环系统 建表脚本
-- 版本：v2.1  日期：2026-10-27
-- 注意：MySQL 服务器时区为 UTC，sample_at / created_at / sys_ts 均以 UTC 存储，展示层需 +8。

create database if not exists donghuan default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
use donghuan;

-- 用户表：审计字段 confirmed_by / maintained_by 的外键目标，因此只能逻辑删除
create table user (
                      id bigint auto_increment primary key,
                      username varchar(64) not null,
                      password_hash varchar(100) not null,
                      role varchar(32) not null,
                      enabled tinyint(1) not null default 1,
                      created_at datetime(3) not null default current_timestamp(3),
                      unique key uk_username (username)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- 设备表
create table device (
                        id bigint auto_increment primary key,
                        device_code varchar(64) not null,
                        name varchar(128) not null,
                        protocol varchar(32) not null,
                        connection_config varchar(1024) null,
                        enabled tinyint(1) not null default 1,
                        last_seen_at datetime(3) null,
                        created_at datetime(3) not null default current_timestamp(3),
                        updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
                        unique key uk_device_code (device_code)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- 测点表：同一设备下 point_code 唯一
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
                       enabled tinyint(1) not null default 1,
                       created_at datetime(3) not null default current_timestamp(3),
                       updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
                       unique key uk_device_point (device_id, point_code),
                       key idx_device (device_id),
                       constraint fk_point_device foreign key (device_id) references device (id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- 采样数据表：sample_at 为设备采样时间，sys_ts 为落库时间
-- quality 当前默认 'OK'，阶段 2 清洗落地后才具备区分能力，届时需调整默认值
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
