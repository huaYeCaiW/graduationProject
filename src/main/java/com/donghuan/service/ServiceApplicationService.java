package com.donghuan.service;

import com.donghuan.model.LoginUser;
import com.donghuan.model.Role;
import com.donghuan.model.ServiceApplicationCommand;
import com.donghuan.model.ServiceApplicationView;
import com.donghuan.model.ServiceItemView;
import com.donghuan.store.ServiceApplicationStore;
import com.donghuan.store.ServiceItemStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ServiceApplicationService {

    private static final Set<String> STATUSES =
            Set.of("PENDING", "APPROVED", "REJECTED", "PROVISIONED");

    private final ServiceApplicationStore applicationStore;
    private final ServiceItemStore serviceItemStore;

    // 种植户提交申请：服务必须启用，棚数不能超过服务上限
    public ServiceApplicationView submit(LoginUser user, ServiceApplicationCommand command) {
        if (user.getRole() != Role.FARMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有种植户可以申请服务");
        }
        if (user.getTenantId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前账号未绑定合作社");
        }
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求不能为空");
        }

        String serviceCode = requireText(command.serviceCode(), "serviceCode", 64);
        ServiceItemView item = serviceItemStore.findByCode(serviceCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "服务项不存在"));
        if (!item.enabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "服务已停用");
        }

        int greenhouseCount = command.greenhouseCount() == null ? 0 : command.greenhouseCount();
        if (greenhouseCount < 1 || greenhouseCount > item.maxGreenhouses()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "申请棚数必须在 1~" + item.maxGreenhouses() + " 之间");
        }

        String contact = requireText(command.contact(), "contact", 64);
        String phone = trimToNull(command.phone());
        if (phone != null && phone.length() > 32) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phone 不能超过 32 字");
        }

        ServiceApplicationCommand normalized =
                new ServiceApplicationCommand(serviceCode, greenhouseCount, contact, phone);
        long id = applicationStore.insert(item.id(), user.getTenantId(), user.getId(), normalized);
        return find(id);
    }

    // 种植户查看自己的申请
    public List<ServiceApplicationView> mine(LoginUser user) {
        return applicationStore.findByApplicant(user.getId());
    }

    // 管理员/运维查看全部申请
    public List<ServiceApplicationView> list(LoginUser user, String status) {
        requireReviewer(user);
        return applicationStore.findAll(normalizeStatus(status));
    }

    // 详情：种植户只能看自己的，管理员/运维看全部
    public ServiceApplicationView get(LoginUser user, long id) {
        ServiceApplicationView application = find(id);
        if (user.getRole() == Role.FARMER && application.applicantId() != user.getId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该申请");
        }
        return application;
    }

    // 审批通过：生成开通任务；服务停用或棚数超限时不允许通过
    public ServiceApplicationView approve(LoginUser user, long id) {
        ServiceApplicationView application = find(id);
        if (!"PENDING".equals(application.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "申请已处理");
        }

        ServiceItemView item = serviceItemStore.findByCode(application.serviceCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "服务项不存在"));
        if (!item.enabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务已停用");
        }
        if (application.greenhouseCount() > item.maxGreenhouses()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "申请棚数超过服务上限");
        }

        long taskId = applicationStore.approveAndCreateTask(id, user.getId());
        if (taskId == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "申请状态已变化");
        }
        return find(id);
    }

    // 驳回：必须填原因
    public ServiceApplicationView reject(LoginUser user, long id, String reason) {
        ServiceApplicationView application = find(id);
        if (!"PENDING".equals(application.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "申请已处理");
        }

        String text = requireText(reason, "驳回原因", 255);
        if (!applicationStore.reject(id, user.getId(), text)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "申请状态已变化");
        }
        return find(id);
    }

    private ServiceApplicationView find(long id) {
        return applicationStore.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "申请单不存在"));
    }

    private void requireReviewer(LoginUser user) {
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.OPS) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看申请列表");
        }
    }

    private String normalizeStatus(String status) {
        String value = trimToNull(status);
        if (value == null) {
            return null;
        }
        value = value.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status 不合法");
        }
        return value;
    }

    private String requireText(String raw, String field, int maxLength) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 不能为空");
        }
        String value = raw.trim();
        if (value.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " 不能超过 " + maxLength + " 字");
        }
        return value;
    }

    private String trimToNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }
}
