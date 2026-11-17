package com.donghuan.model.view;

import java.time.Instant;
import java.util.List;

/**
 * 服务申请视图：种植户看自己的申请，管理员看待审批需求
 */
public record ServiceApplicationView(
        long id,
        String applicationNo,
        long tenantId,
        String tenantName,
        long applicantId,
        String applicantName,
        String greenhouseName,
        String phone,
        List<DemandPointView> points,
        String status,
        Instant createdAt,
        String rejectReason
) {
    // 需求项：测点类型与数量，具体测点编码在开通时生成
    public record DemandPointView(String type, Integer count) {
    }
}
