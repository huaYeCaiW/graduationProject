package com.donghuan.model.command;

public record WorkOrderCommand(
        String greenhouseName,
        String applicantName,
        String phone,
        String description
) {
}
