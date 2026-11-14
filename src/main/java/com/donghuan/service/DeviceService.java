package com.donghuan.service;

import com.donghuan.acquire.AcquisitionService;
import com.donghuan.common.DeviceConfig;
import com.donghuan.common.DeviceProperties;
import com.donghuan.model.DeviceCommand;
import com.donghuan.model.LoginUser;
import com.donghuan.model.Protocol;
import com.donghuan.model.TenantScope;
import com.donghuan.store.DeviceStore;
import com.donghuan.store.PointRefCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {
    private final DeviceProperties deviceProperties;
    private final DeviceStore deviceStore;
    private final PointRefCache refCache;
    private final AcquisitionService acquisitionService;

    // 过滤后返回 list
    public Collection<DeviceConfig> list(LoginUser user) {
        TenantScope scope = TenantScope.of(user);
        return deviceProperties.getDevices().stream()
                // filter 每个 device canSee == true 通过
                .filter(d -> scope.canSee(d.getTenantId()))
                .toList();
    }

    // 过滤后查不到就是 404
    public DeviceConfig get(LoginUser user, String deviceCode) {
        TenantScope scope = TenantScope.of(user);
        return deviceProperties.findDevice(deviceCode)
                .filter(d -> scope.canSee(d.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // 已删除设备列表：恢复入口用，按租户过滤
    public List<DeviceCommand> findDisabled(LoginUser user) {
        return deviceStore.findDisabledDevices(TenantScope.of(user));
    }

    // 新增设备：写库后加入内存并接入适配器
    public void create(DeviceCommand cmd) {
        String deviceCode = requireText(cmd.deviceCode(), "deviceCode");
        DeviceCommand device = normalize(deviceCode, cmd);

        try {
            deviceStore.insert(device);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "设备编码已存在");
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "租户不存在");
        }

        // 写库成功后同步内存：只新增这一台设备，其他对象保持原样
        DeviceConfig config = new DeviceConfig();
        config.setDeviceCode(device.deviceCode());
        config.setName(device.name());
        config.setTenantId(device.tenantId());
        config.setProtocol(Protocol.from(device.protocol()));
        DeviceConfig.Connection connection = new DeviceConfig.Connection();
        connection.setConfig(device.connectionConfig());
        config.setConnection(connection);
        deviceProperties.addDevice(config);

        // 新设备免重启接入：MQTT 先订阅；Modbus 没有测点会跳过，等新增测点后再接入
        acquisitionService.connect(config);
    }

    // 修改设备：以路径上的 deviceCode 为准，body 里的 deviceCode 不参与更新
    public void update(LoginUser user, String deviceCode, DeviceCommand cmd) {
        TenantScope scope = TenantScope.of(user);
        DeviceConfig current = deviceProperties.findDevice(deviceCode)
                .filter(d -> scope.canSee(d.getTenantId()))
                .orElse(null);

        // 内存里没有：只有命中逻辑删除记录且 body 带 enabled=true 才走恢复
        if (current == null) {
            restore(scope, deviceCode, cmd);
            return;
        }

        DeviceCommand device = normalize(deviceCode, cmd);
        Protocol targetProtocol = Protocol.from(device.protocol());
        String oldConnectionConfig = current.getConnection() == null
                ? null : current.getConnection().getConfig();
        // 协议或连接串变化才需要重连，只改名称/租户不打断采集
        boolean reconnect = targetProtocol != current.getProtocol()
                || !Objects.equals(oldConnectionConfig, device.connectionConfig());

        try {
            deviceStore.update(device);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "租户不存在");
        }

        // 写库成功后再动内存；先按旧配置断开，再改字段，最后按新配置连接
        if (reconnect) {
            try {
                acquisitionService.disconnect(current);
            } catch (Exception e) {
                log.warn("停止设备采集失败：deviceCode={}", deviceCode, e);
            }
        }

        current.setName(device.name());
        current.setTenantId(device.tenantId());
        current.setProtocol(targetProtocol);
        if (current.getConnection() == null) {
            current.setConnection(new DeviceConfig.Connection());
        }
        current.getConnection().setConfig(device.connectionConfig());

        if (reconnect) {
            acquisitionService.connect(current);
        }
    }

    // 恢复逻辑删除的设备：复用原 device_code，设备与测点一起重新启用
    private void restore(TenantScope scope, String deviceCode, DeviceCommand cmd) {
        DeviceCommand stored = deviceStore.findDisabled(deviceCode);
        if (stored == null || !Boolean.TRUE.equals(cmd.enabled())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (!scope.canSee(stored.tenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        DeviceCommand device = normalize(deviceCode, cmd);
        try {
            deviceStore.restore(device);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "租户不存在");
        }

        // 库里的设备与测点已启用，先补回 point_id 映射，再把这一台装载进内存并接入
        refCache.refresh();
        DeviceConfig config = deviceProperties.loadDevice(deviceCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "设备恢复失败"));
        deviceProperties.addDevice(config);
        acquisitionService.connect(config);
    }

    // 删除设备：逻辑删库、停采集、移出内存；历史采样数据保留
    public void delete(LoginUser user, String deviceCode) {
        DeviceConfig current = get(user, deviceCode);

        deviceStore.disable(deviceCode);

        // 先停适配器，再移出内存；断开失败也继续删，解析层查不到设备会丢弃后续数据
        try {
            acquisitionService.disconnect(current);
        } catch (Exception e) {
            log.warn("停止设备采集失败：deviceCode={}", deviceCode, e);
        }

        deviceProperties.removeDevice(deviceCode);
    }

    // 校验并归一化，deviceCode 以路径为准
    private DeviceCommand normalize(String deviceCode, DeviceCommand cmd) {
        return new DeviceCommand(
                deviceCode,
                cmd.tenantId(),
                requireText(cmd.name(), "name"),
                normalizeProtocol(cmd.protocol()),
                cmd.connectionConfig() == null ? null : cmd.connectionConfig().trim(),
                cmd.enabled());
    }

    // 协议统一小写，只接受 mqtt / modbus
    private String normalizeProtocol(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "protocol 不能为空");
        }
        try {
            return Protocol.from(raw).name().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "protocol 仅支持 mqtt / modbus");
        }
    }

    private String requireText(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 不能为空");
        }
        return raw.trim();
    }
}
