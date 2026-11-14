package com.donghuan.service;

import com.donghuan.acquire.AcquisitionService;
import com.donghuan.common.DeviceConfig;
import com.donghuan.common.DeviceProperties;
import com.donghuan.model.GreenhouseCommand;
import com.donghuan.model.LoginUser;
import com.donghuan.model.ModbusPointSpec;
import com.donghuan.model.Protocol;
import com.donghuan.model.ProvisionCommand;
import com.donghuan.model.ProvisionPointCommand;
import com.donghuan.model.ProvisionTaskView;
import com.donghuan.model.Role;
import com.donghuan.model.ServiceApplicationView;
import com.donghuan.model.ServiceItemView;
import com.donghuan.model.ServiceMetricTemplate;
import com.donghuan.store.DeviceStore;
import com.donghuan.store.PointRefCache;
import com.donghuan.store.ProvisionTaskStore;
import com.donghuan.store.ServiceApplicationStore;
import com.donghuan.store.ServiceItemStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProvisionTaskService {

    private static final Set<String> STATUSES = Set.of("PENDING", "RUNNING", "DONE", "CANCELLED");

    private final ProvisionTaskStore taskStore;
    private final ServiceApplicationStore applicationStore;
    private final ServiceItemStore serviceItemStore;
    private final DeviceStore deviceStore;
    private final DeviceProperties deviceProperties;
    private final PointRefCache refCache;
    private final AcquisitionService acquisitionService;

    public List<ProvisionTaskView> list(LoginUser user, String status) {
        requireOps(user);
        return taskStore.findAll(normalizeStatus(status));
    }

    public ProvisionTaskView get(LoginUser user, long id) {
        requireOps(user);
        return find(id);
    }

    // 执行开通：校验棚数与测点后写库，再刷新缓存并热接入，不重启出数
    public ProvisionTaskView provision(LoginUser user, long taskId, ProvisionCommand command) {
        requireOps(user);

        ProvisionTaskView task = find(taskId);
        if (!"PENDING".equals(task.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "开通任务已处理");
        }

        ServiceApplicationView application = applicationStore.findById(task.applicationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "申请单不存在"));
        if (!"APPROVED".equals(application.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "申请单当前状态不可开通");
        }

        ServiceItemView item = serviceItemStore.findByCode(application.serviceCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "服务项不存在"));

        if (command == null || command.greenhouses() == null || command.greenhouses().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "greenhouses 不能为空");
        }
        if (command.greenhouses().size() != task.greenhouseCount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "开通棚数必须等于申请棚数：" + task.greenhouseCount());
        }

        List<GreenhouseCommand> greenhouses = normalizeGreenhouses(command.greenhouses(), item);
        String remark = trimToNull(command.remark());
        if (remark != null && remark.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remark 不能超过 255 字");
        }

        List<String> deviceCodes;
        try {
            deviceCodes = taskStore.provision(taskId, user.getId(), application, item, greenhouses, remark);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "设备或测点已存在，请刷新后重试");
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开通数据不合法，请检查连接配置与测点");
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }

        hotApply(deviceCodes);
        return find(taskId);
    }

    // 校验每台大棚的登记信息、连接配置和测点，全部通过后才进事务写库
    private List<GreenhouseCommand> normalizeGreenhouses(List<GreenhouseCommand> raw,
                                                         ServiceItemView item) {
        Map<String, ServiceMetricTemplate> templates = item.metrics().stream()
                .collect(Collectors.toMap(ServiceMetricTemplate::metricCode, Function.identity()));

        Set<String> deviceCodes = new HashSet<>();
        List<GreenhouseCommand> greenhouses = new ArrayList<>();

        for (GreenhouseCommand greenhouse : raw) {
            if (greenhouse == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "大棚登记信息不能为空");
            }

            String deviceCode = requireCode(greenhouse.deviceCode(), "deviceCode");
            if (!deviceCodes.add(deviceCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "设备编码重复：" + deviceCode);
            }
            String name = requireText(greenhouse.name(), "大棚名称", 128);
            Protocol protocol = parseProtocol(greenhouse.protocol());

            String connectionConfig = trimToNull(greenhouse.connectionConfig());
            if (connectionConfig != null && connectionConfig.length() > 1024) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "连接配置不能超过 1024 字：" + deviceCode);
            }
            if (deviceStore.findId(deviceCode) == null) {
                if (deviceStore.findDisabled(deviceCode) != null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "设备已删除，请先恢复：" + deviceCode);
                }
                if (connectionConfig == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "新设备必须填写连接配置：" + deviceCode);
                }
            }

            List<ProvisionPointCommand> points =
                    normalizePoints(protocol, greenhouse.points(), templates);

            greenhouses.add(new GreenhouseCommand(deviceCode, name,
                    protocol.name().toLowerCase(Locale.ROOT), connectionConfig, points));
        }
        return greenhouses;
    }

    // 测点必须覆盖全部指标模板，且不能出现模板外的指标
    private List<ProvisionPointCommand> normalizePoints(Protocol protocol,
                                                        List<ProvisionPointCommand> raw,
                                                        Map<String, ServiceMetricTemplate> templates) {
        if (raw == null || raw.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "测点不能为空");
        }

        Set<String> metricCodes = new HashSet<>();
        List<ProvisionPointCommand> points = new ArrayList<>();

        for (ProvisionPointCommand point : raw) {
            if (point == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "测点配置不能为空");
            }

            String metricCode = requireText(point.metricCode(), "metricCode", 64);
            if (!templates.containsKey(metricCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "指标不在服务模板中：" + metricCode);
            }
            if (!metricCodes.add(metricCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "指标重复：" + metricCode);
            }

            String address = requireText(point.address(), "address", 1024);
            String timeAddress = trimToNull(point.timeAddress());
            if (timeAddress != null && timeAddress.length() > 255) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timeAddress 不能超过 255 字");
            }
            String dataType = trimToNull(point.dataType());
            if (dataType != null && dataType.length() > 16) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dataType 不能超过 16 字");
            }

            if (protocol == Protocol.MODBUS) {
                address = address.replaceAll("\\s+", "");
                try {
                    ModbusPointSpec spec = ModbusPointSpec.parse(address, dataType);
                    dataType = spec.dataType().name().toLowerCase(Locale.ROOT);
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Modbus 地址无效：" + e.getMessage());
                }
                timeAddress = null;
            } else {
                if (!address.startsWith("$")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "MQTT 地址必须是 JSON 路径（以 $ 开头）");
                }
                dataType = dataType == null ? null : dataType.toLowerCase(Locale.ROOT);
            }

            Integer freq = point.freqSeconds();
            if (freq != null && freq <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采集周期必须大于 0");
            }
            requireRange(point.minValue(), point.maxValue(), "告警阈值");
            requireRange(point.rangeMin(), point.rangeMax(), "物理量程");

            points.add(new ProvisionPointCommand(metricCode, address, timeAddress, dataType,
                    point.minValue(), point.maxValue(), point.rangeMin(), point.rangeMax(), freq));
        }

        for (String metricCode : templates.keySet()) {
            if (!metricCodes.contains(metricCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "缺少指标测点：" + metricCode);
            }
        }
        return points;
    }

    // 写库成功后刷新 point_id 映射；已接入的设备按新配置重建，新设备直接接入
    private void hotApply(List<String> deviceCodes) {
        refCache.refresh();

        for (String deviceCode : deviceCodes) {
            DeviceConfig fresh = deviceProperties.loadDevice(deviceCode).orElse(null);
            if (fresh == null) {
                log.warn("开通后装载设备失败：deviceCode={}", deviceCode);
                continue;
            }

            DeviceConfig existing = deviceProperties.findDevice(deviceCode).orElse(null);
            if (existing != null) {
                try {
                    acquisitionService.disconnect(existing);
                } catch (Exception e) {
                    log.warn("开通后停止旧设备采集失败：deviceCode={}", deviceCode, e);
                }
                deviceProperties.removeDevice(deviceCode);
            }

            deviceProperties.addDevice(fresh);
            acquisitionService.connect(fresh);
        }
    }

    private ProvisionTaskView find(long id) {
        return taskStore.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "开通任务不存在"));
    }

    private void requireOps(LoginUser user) {
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.OPS) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作开通任务");
        }
    }

    private Protocol parseProtocol(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "protocol 不能为空");
        }
        try {
            return Protocol.from(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "protocol 仅支持 mqtt / modbus");
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
