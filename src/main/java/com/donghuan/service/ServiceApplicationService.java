package com.donghuan.service;

import com.donghuan.model.command.RejectCommand;
import com.donghuan.model.security.LoginUser;
import com.donghuan.model.enums.Role;
import com.donghuan.model.command.ServiceApplicationCommand;
import com.donghuan.model.view.ServiceApplicationView;
import com.donghuan.store.ServiceApplicationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ServiceApplicationService {

    private static final Set<String> STATUSES =
            Set.of("PENDING", "APPROVED", "REJECTED", "INSTALLED");

    private final ServiceApplicationStore applicationStore;
    private static final Set<String> ALLOWED_TYPES =
            Set.of("temperature", "humidity", "soil", "light");
    private static final int MAX_POINTS_PER_TYPE = 20;

    // 种植户提交申请只存需求
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

        String greenhouseName = requireText(command.greenhouseName(), "棚名", 128);
        String applicantName = requireText(command.applicantName(), "申请人", 64);
        String phone = requireText(command.phone(), "联系电话", 32);
        if (phone.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "联系电话格式错误");
        }
        List<ServiceApplicationCommand.DemandPoint> points = normalizePoints(command.points());
        ServiceApplicationCommand normalized =
                new ServiceApplicationCommand(greenhouseName, applicantName, points, phone);

        long id = applicationStore.insert(user.getTenantId(), user.getId(), normalized);
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

    // 管理员批准
    public ServiceApplicationView approve(LoginUser user, long id) {
        if (user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前帐号无权限操作");
        }
        if (applicationStore.approve(id, user.getId()) == 0) {
            find(id); // 不存在直接抛 404
            throw new ResponseStatusException(HttpStatus.CONFLICT, "申请单当前状态不可审批");
        }
        return find(id);
    }

    // 管理员拒绝
    public ServiceApplicationView reject(LoginUser user, long id, RejectCommand rejectCommand) {
        if (user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前帐号无权限操作");
        }
        String reason = requireText(rejectCommand.reason(), "驳回理由", 255);
        if (applicationStore.reject(id, user.getId(), reason) == 0) {
            find(id);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "申请单当前状态不可审批");
        }
        return find(id);
    }

    // 运维安装设备后确认
    public ServiceApplicationView confirm(LoginUser user, long id) {
        if (user.getRole() == Role.FARMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前帐号无权限操作");
        }
        if (applicationStore.confirm(id, user.getId()) == 0) {
            find(id);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "申请单当前状态不可确认");
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

    private List<ServiceApplicationCommand.DemandPoint> normalizePoints(
            List<ServiceApplicationCommand.DemandPoint> points) {
        if (points == null || points.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "至少选择一个测点");
        }

        Set<String> seen = new HashSet<>();
        List<ServiceApplicationCommand.DemandPoint> result = new ArrayList<>();

        for (ServiceApplicationCommand.DemandPoint point : points) {
            if (point == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "测点不能为空");
            }

            String type = requireText(point.type(), "测点类型", 32).toLowerCase(Locale.ROOT);
            if (!ALLOWED_TYPES.contains(type)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "不支持的测点类型：" + type);
            }
            if (point.count() == null || point.count() < 1 || point.count() > MAX_POINTS_PER_TYPE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "测点数量必须在1~" + MAX_POINTS_PER_TYPE + "之间");
            }
            if (!seen.add(type)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "测点类型重复：" + type);
            }

            result.add(new ServiceApplicationCommand.DemandPoint(type, point.count()));
        }
        return result;
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
