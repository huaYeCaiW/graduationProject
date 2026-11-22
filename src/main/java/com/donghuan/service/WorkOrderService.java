package com.donghuan.service;

import com.donghuan.common.Texts;
import com.donghuan.model.command.CancelCommand;
import com.donghuan.model.command.CompleteCommand;
import com.donghuan.model.command.WorkOrderCommand;
import com.donghuan.model.enums.Role;
import com.donghuan.model.security.LoginUser;
import com.donghuan.model.view.WorkOrderView;
import com.donghuan.store.WorkOrderStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private static final Set<String> STATUSES =
            Set.of("PENDING", "IN_PROGRESS", "WAITING_ACCEPT", "DONE", "CANCELLED");

    private final WorkOrderStore workOrderStore;
    public WorkOrderView submit(LoginUser user, WorkOrderCommand command) {
        if (user.getRole() != Role.FARMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有种植户可以报修");
        }
        if (user.getTenantId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前账号未绑定合作社");
        }
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求不能为空");
        }

        String greenhouseName = Texts.require(command.greenhouseName(), "大棚名称", 128);
        String applicantName = Texts.require(command.applicantName(), "申请人", 64);
        String phone = Texts.require(command.phone(), "联系电话", 32);
        if (phone.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "电话格式错误");
        }
        String description = Texts.require(command.description(), "问题描述", 500);

        WorkOrderCommand normalized =
                new WorkOrderCommand(greenhouseName, applicantName, phone, description);
        long id = workOrderStore.insert(user.getTenantId(), user.getId(), normalized);
        return find(id);
    }

    public List<WorkOrderView> mine(LoginUser user) {
        return workOrderStore.findByApplicant(user.getId());
    }

    public List<WorkOrderView> list(LoginUser user, String statusFilter) {
        requireReviewer(user);
        return workOrderStore.findAll(normalizeStatus(statusFilter));
    }

    public WorkOrderView get(LoginUser user, long id) {
        WorkOrderView order = find(id);
        if (user.getRole() == Role.FARMER && order.applicantId() != user.getId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该工单");
        }
        return order;
    }

    public WorkOrderView accept(LoginUser user, long id) {
        if (user.getRole() != Role.OPS) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权限操作");
        }
        if (workOrderStore.accept(id, user.getId()) == 0) {
            find(id);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前工单状态不可接");
        }
        return find(id);
    }

    public WorkOrderView complete(LoginUser user, long id, CompleteCommand command) {
        if (user.getRole() != Role.OPS) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无操作权限");
        }
        String note = Texts.require(command == null ? null : command.note(), "完工说明", 255);
        if (workOrderStore.complete(id, user.getId(), note) == 0) {
            find(id);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前工单状态不可完成");
        }
        return find(id);
    }

    public WorkOrderView confirm(LoginUser user, long id) {
        if (user.getRole() != Role.FARMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权限操作");
        }
        WorkOrderView order = find(id);
        if (order.applicantId() != user.getId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权验收该工单");
        }
        if (workOrderStore.confirm(id) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工单当前状态不可验收");
        }
        return find(id);
    }

    public WorkOrderView cancel(LoginUser user, long id, CancelCommand command) {
        if (user.getRole() == Role.FARMER) {
            WorkOrderView order = find(id);
            if (order.applicantId() != user.getId()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限取消该工单");
            }
            String reason = Texts.trimToNull(command == null ? null : command.reason());
            if (reason != null && reason.length() > 255) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "取消原因不能超过 255 字");
            }
            if (workOrderStore.cancelByFarmer(id, user.getId(), reason) == 0) {
                find(id);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前工单状态不能取消");
            }
            return find(id);
        }

        if (user.getRole() == Role.ADMIN) {
            String reason = Texts.require(command == null ? null : command.reason(), "取消原因", 255);
            if (workOrderStore.cancelByAdmin(id, user.getId(), reason) == 0) {
                find(id);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前工单状态不能取消");
            }
            return find(id);
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无操作权限");
    }

    private WorkOrderView find(long id) {
        return workOrderStore.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工单不存在"));
    }

    private void requireReviewer(LoginUser user) {
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.OPS) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看工单列表");
        }
    }

    private String normalizeStatus(String raw) {
        String value = Texts.trimToNull(raw);
        if (value == null) return null;
        value = value.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status 不合法");
        }
        return value;
    }
}
