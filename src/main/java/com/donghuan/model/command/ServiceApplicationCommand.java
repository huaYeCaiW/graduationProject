package com.donghuan.model.command;

import java.util.List;

/**
 * 种植户提交服务申请的命令
 */
public record ServiceApplicationCommand(
        String greenhouseName,      // 大棚名
        String applicantName,       // 申请人
        List<DemandPoint> points,   // 测点
        String phone                // 联系电话
) {
    // 测点记录测点类型（温度、湿度等），各测点数量
    public record DemandPoint(String type, Integer count) {
    }
}
