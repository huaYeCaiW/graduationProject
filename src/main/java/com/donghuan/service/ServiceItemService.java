package com.donghuan.service;

import com.donghuan.model.LoginUser;
import com.donghuan.model.Role;
import com.donghuan.model.ServiceItemCommand;
import com.donghuan.model.ServiceItemView;
import com.donghuan.model.ServiceMetricTemplate;
import com.donghuan.store.ServiceItemStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ServiceItemService {

    private final ServiceItemStore serviceItemStore;

    // 管理员能看到停用项，其他角色只看启用项
    public List<ServiceItemView> list(LoginUser user) {
        return serviceItemStore.findAll(user.getRole() == Role.ADMIN);
    }

    public ServiceItemView get(LoginUser user, String code) {
        ServiceItemView item = find(code);
        if (!item.enabled() && user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return item;
    }

    public ServiceItemView create(ServiceItemCommand command) {
        ServiceItemCommand normalized = normalize(command, true);
        if (serviceItemStore.findByCode(normalized.code()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务编码已存在");
        }
        serviceItemStore.insert(normalized);
        return find(normalized.code());
    }

    public ServiceItemView update(String code, ServiceItemCommand command) {
        ServiceItemView existing = find(code);
        boolean enabled = command == null || command.enabled() == null
                ? existing.enabled() : command.enabled();
        ServiceItemCommand normalized = normalize(command, false, enabled);
        serviceItemStore.update(existing.id(), normalized);
        return find(existing.code());
    }

    public void disable(String code) {
        ServiceItemView existing = find(code);
        serviceItemStore.disable(existing.id());
    }

    private ServiceItemView find(String code) {
        return serviceItemStore.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "服务项不存在"));
    }

    private ServiceItemCommand normalize(ServiceItemCommand command, boolean checkCode) {
        return normalize(command, checkCode, true);
    }

    private ServiceItemCommand normalize(ServiceItemCommand command, boolean checkCode, boolean defaultEnabled) {
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求不能为空");
        }

        String code = checkCode ? requireCode(command.code(), "code") : null;
        String name = requireText(command.name(), "name", 128);
        String description = command.description() == null ? null : command.description().trim();
        if (description != null && description.length() > 512) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "description 不能超过 512 字");
        }

        int maxGreenhouses = command.maxGreenhouses() == null ? 1 : command.maxGreenhouses();
        int maxPoints = command.maxPointsPerGreenhouse() == null ? 4 : command.maxPointsPerGreenhouse();
        if (maxGreenhouses < 1 || maxGreenhouses > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxGreenhouses 必须在 1~100");
        }
        if (maxPoints < 1 || maxPoints > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxPointsPerGreenhouse 必须在 1~100");
        }

        List<ServiceMetricTemplate> metrics = command.metrics();
        if (metrics == null || metrics.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "metrics 不能为空");
        }
        if (metrics.size() > maxPoints) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "指标模板数量不能超过单棚测点上限");
        }

        Set<String> metricCodes = new HashSet<>();
        List<ServiceMetricTemplate> normalizedMetrics = metrics.stream()
                .map(metric -> normalizeMetric(metric, metricCodes))
                .toList();

        return new ServiceItemCommand(code, name, description, maxGreenhouses, maxPoints,
                command.enabled() == null ? defaultEnabled : command.enabled(), normalizedMetrics);
    }

    private ServiceMetricTemplate normalizeMetric(ServiceMetricTemplate metric, Set<String> metricCodes) {
        if (metric == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "指标模板不能为空");
        }
        String metricCode = requireCode(metric.metricCode(), "metricCode");
        if (!metricCodes.add(metricCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "指标编码重复：" + metricCode);
        }
        String name = requireText(metric.name(), "指标名称", 128);
        String unit = metric.unit() == null ? null : metric.unit().trim();
        if (unit != null && unit.length() > 16) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单位不能超过 16 字");
        }
        String dataType = metric.dataType() == null || metric.dataType().isBlank()
                ? "double" : metric.dataType().trim().toLowerCase(Locale.ROOT);
        if (metric.freqSeconds() != null && metric.freqSeconds() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采集周期必须大于 0");
        }
        requireRange(metric.rangeMin(), metric.rangeMax(), "物理量程");
        requireRange(metric.defaultMin(), metric.defaultMax(), "告警阈值");

        return new ServiceMetricTemplate(metricCode, name, unit, dataType,
                metric.defaultMin(), metric.defaultMax(), metric.rangeMin(), metric.rangeMax(),
                metric.freqSeconds());
    }

    private void requireRange(Double min, Double max, String field) {
        if (min != null && max != null && min > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " min 不能大于 max");
        }
    }

    private String requireCode(String raw, String field) {
        String value = requireText(raw, field, 64);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " 只能包含字母、数字、下划线和中划线");
        }
        return value;
    }

    private String requireText(String raw, String field, int maxLength) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 不能为空");
        }
        String value = raw.trim();
        if (value.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 不能超过 " + maxLength + " 字");
        }
        return value;
    }
}
