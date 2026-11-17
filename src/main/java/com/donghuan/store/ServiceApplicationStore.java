package com.donghuan.store;

import com.donghuan.model.command.ServiceApplicationCommand;
import com.donghuan.model.view.ServiceApplicationView;

import java.util.List;
import java.util.Optional;

public interface ServiceApplicationStore {

    // 提交申请，申请插入申请表
    long insert(long tenantId, long applicantId, ServiceApplicationCommand command);
    // 管理员审批
    int approve(long id, long reviewerId);
    // 拒绝
    int reject(long id, long reviewerId, String reason);
    // 运维确认
    int confirm(long id, long reviewerId);

    Optional<ServiceApplicationView> findById(long id);

    List<ServiceApplicationView> findByApplicant(long applicantId);

    List<ServiceApplicationView> findAll(String status);
}
