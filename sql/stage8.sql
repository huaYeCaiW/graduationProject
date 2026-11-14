-- 阶段 8 迁移脚本：服务申请、审批、开通
-- 说明：阶段 8 的建表与种子数据已并入 schema.sql / seed.sql，全新环境直接执行那两个脚本；
--       本文件仅保留给已存在的旧库做增量升级。
-- 用法：mysql -uroot -p donghuan < sql/stage8.sql
-- 可重复执行；已有表或字段会跳过

use donghuan;

-- device 增加开通来源，便于按申请单追溯已纳入的设备
set @has_application_id := (
    select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'device' and column_name = 'application_id'
);
set @ddl := if(@has_application_id = 0,
    'alter table device add column application_id bigint null, add key idx_device_application (application_id)',
    'select 1');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

create table if not exists service_item (
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

create table if not exists service_metric (
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

create table if not exists service_application (
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

create table if not exists provision_task (
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
create table if not exists provision_task_device (
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

-- 种子服务项：标准大棚监测（温湿度、土壤湿度、光照）
insert ignore into service_item (id, code, name, description, max_greenhouses, max_points_per_greenhouse, enabled)
values (1, 'greenhouse-basic', '标准大棚监测服务', '温度、空气湿度、土壤湿度、光照四项基础监测', 2, 6, 1);

insert ignore into service_metric
    (id, service_item_id, metric_code, name, unit, data_type, default_min, default_max, range_min, range_max, freq_seconds, sort_no)
values
    (1, 1, 'temperature', '温度', '℃', 'double', 18, 38, -40, 80, 5, 1),
    (2, 1, 'humidity', '空气湿度', '%', 'double', 30, 85, 0, 100, 5, 2),
    (3, 1, 'soil', '土壤湿度', '%', 'double', 10, 80, 0, 100, 5, 3),
    (4, 1, 'light', '光照', 'lx', 'double', 0, 50000, 0, 200000, 5, 4);

-- 回填已完成任务的设备快照，保证历史任务详情不因设备复用而变化
insert ignore into provision_task_device (task_id, device_id, device_code, name, protocol, point_count)
select p.id, d.id, d.device_code, d.name, d.protocol,
       (select count(*) from point pt where pt.device_id = d.id and pt.enabled = 1)
from provision_task p
join service_application a on a.id = p.application_id
join device d on d.application_id = a.id and d.enabled = 1
where p.status = 'DONE';
