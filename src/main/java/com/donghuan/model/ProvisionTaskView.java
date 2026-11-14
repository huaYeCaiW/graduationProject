package com.donghuan.model;

import java.time.Instant;
import java.util.List;

/**
 * 开通任务视图：运维看任务、申请信息、指标模板和已纳入设备
 */
public record ProvisionTaskView(
        long id,
        long applicationId,
        String applicationNo,
        String serviceCode,
        String serviceName,
        long tenantId,
        String tenantName,
        int greenhouseCount,
        String status,
        String remark,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        List<ServiceMetricTemplate> metrics,
        List<ProvisionDeviceView> devices
) {
}
