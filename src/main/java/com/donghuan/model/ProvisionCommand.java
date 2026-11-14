package com.donghuan.model;

import java.util.List;

/**
 * 执行开通任务的命令
 */
public record ProvisionCommand(
        List<GreenhouseCommand> greenhouses,
        String remark
) {
}
