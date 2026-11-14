package com.donghuan.model;

import java.time.Instant;

/**
 * 服务申请视图：种植户看自己的申请，管理员看审批列表
 */
public record ServiceApplicationView(
        long id,
        String applicationNo,
        String serviceCode,
        String serviceName,
        long tenantId,
        String tenantName,
        long applicantId,
        String applicantName,
        int greenhouseCount,
        String contact,
        String phone,
        String status,
        String rejectReason,
        Long provisionTaskId,
        String provisionStatus,
        Instant reviewedAt,
        Instant createdAt
) {
}
