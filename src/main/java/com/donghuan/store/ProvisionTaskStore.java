package com.donghuan.store;

import com.donghuan.model.GreenhouseCommand;
import com.donghuan.model.ProvisionTaskView;
import com.donghuan.model.ServiceApplicationView;
import com.donghuan.model.ServiceItemView;

import java.util.List;
import java.util.Optional;

public interface ProvisionTaskStore {

    List<ProvisionTaskView> findAll(String status);

    Optional<ProvisionTaskView> findById(long id);

    // 执行开通：写设备/测点并更新申请、任务状态；返回本次纳入的设备编码
    List<String> provision(long taskId, long userId, ServiceApplicationView application,
                           ServiceItemView item, List<GreenhouseCommand> greenhouses, String remark);
}
