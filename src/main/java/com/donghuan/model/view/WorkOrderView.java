package com.donghuan.model.view;

import java.time.Instant;

public record WorkOrderView(
        long id,
        String orderNo,
        long tenantId,
        String tenantName,
        long applicantId,
        String applicantName,
        String greenhouseName,
        String phone,
        String description,
        String status,
        Long acceptedBy,
        String acceptedByName,
        Instant acceptedAt,
        Long completedBy,
        String completedByName,
        Instant completedAt,
        String completeNote,
        Instant confirmedAt,
        Long cancelledBy,
        String cancelledByName,
        Instant cancelledAt,
        String cancelReason,
        Instant createdAt
) {
}
