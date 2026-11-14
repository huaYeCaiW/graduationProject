package com.donghuan.store;

import com.donghuan.model.ServiceApplicationCommand;
import com.donghuan.model.ServiceApplicationView;

import java.util.List;
import java.util.Optional;

public interface ServiceApplicationStore {

    long insert(long serviceItemId, long tenantId, long applicantId, ServiceApplicationCommand command);

    Optional<ServiceApplicationView> findById(long id);

    List<ServiceApplicationView> findByApplicant(long applicantId);

    List<ServiceApplicationView> findAll(String status);

    // 审批通过并生成开通任务；返回 0 表示申请状态已变化
    long approveAndCreateTask(long id, long reviewerId);

    // 驳回；返回 false 表示申请状态已变化
    boolean reject(long id, long reviewerId, String reason);
}
