package com.donghuan.model;

/**
 * 种植户提交服务申请的命令
 */
public record ServiceApplicationCommand(
        String serviceCode,
        Integer greenhouseCount,
        String contact,
        String phone
) {
}
