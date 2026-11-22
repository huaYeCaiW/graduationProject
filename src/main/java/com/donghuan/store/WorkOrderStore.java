package com.donghuan.store;

import com.donghuan.model.command.WorkOrderCommand;
import com.donghuan.model.view.WorkOrderView;

import java.util.List;
import java.util.Optional;

public interface WorkOrderStore {
    long insert(long tenantId, long applicantId, WorkOrderCommand command);
    int accept(long id, long opsId);
    int complete(long id, long opsId, String reason);
    int confirm(long id);
    int cancelByFarmer(long id, long userId, String reason);
    int cancelByAdmin(long id, long userId, String reason);
    Optional<WorkOrderView> findById(long id);
    List<WorkOrderView> findByApplicant(long applicantId);
    List<WorkOrderView> findAll(String status);
}
