package com.donghuan.service;

import com.donghuan.acquire.AcquisitionService;
import com.donghuan.common.DeviceConfig;
import com.donghuan.model.LoginUser;
import com.donghuan.model.ModbusPointSpec;
import com.donghuan.model.PointCommand;
import com.donghuan.model.PointValue;
import com.donghuan.model.Protocol;
import com.donghuan.model.TenantScope;
import com.donghuan.store.DeviceStore;
import com.donghuan.store.PointAdminStore;
import com.donghuan.store.PointRefCache;
import com.donghuan.store.PointStore;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PointService {
    private final PointStore pointStore;
    private final DeviceService deviceService;
    private final DeviceStore deviceStore;
    private final PointAdminStore pointAdminStore;
    private final PointRefCache refCache;
    private final AcquisitionService acquisitionService;

    public PointValue findLatest(LoginUser user, String deviceCode, String pointCode) {
        TenantScope scope = TenantScope.of(user);
        return pointStore.findLatest(scope, deviceCode, pointCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public List<PointValue> findByRange(LoginUser user, String deviceCode, String pointCode, Instant startTime, Instant endTime) {
        TenantScope scope = TenantScope.of(user);
        return pointStore.findByRange(scope, deviceCode, pointCode, startTime, endTime);
    }

    public void updateThreshold(LoginUser user, String deviceCode, String pointCode, Double min, Double max) {
        if (min != null && max != null && min > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "min 不能大于 max");
        }
        TenantScope scope = TenantScope.of(user);
        boolean ok = pointStore.updateThreshold(scope, deviceCode, pointCode, min, max);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    public void updateRange(LoginUser user, String deviceCode, String pointCode,
                            Double rangeMin, Double rangeMax) {
        if (rangeMin != null && rangeMax != null && rangeMin > rangeMax) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rangeMin 不能大于 rangeMax");
        }
        TenantScope scope = TenantScope.of(user);
        boolean ok = pointStore.updateRange(scope, deviceCode, pointCode, rangeMin, rangeMax);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    // 新增测点
    public void create(LoginUser user, String deviceCode, PointCommand cmd) {
        DeviceConfig device = deviceService.get(user, deviceCode);
        long deviceId = requireDeviceId(deviceCode);
        String pointCode = requireText(cmd.pointCode(), "pointCode");
        ValidatedPoint validated = normalize(device.getProtocol(), pointCode, cmd, null);

        try {
            pointAdminStore.insert(deviceId, validated.command());
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "测点编码已存在");
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "测点数据不合法");
        }

        // 先刷新 point_id 映射，再加入内存，避免新测点的数据因查不到 id 被跳过
        refCache.refresh();
        device.getPoints().add(toConfigPoint(validated));

        // 新设备刚有第一个测点时触发接入；已接入的适配器会直接返回
        acquisitionService.connect(device);
    }

    // 修改测点：以路径上的 pointCode 为准，body 里的 pointCode 不参与更新
    public void update(LoginUser user, String deviceCode, String pointCode, PointCommand cmd) {
        DeviceConfig device = deviceService.get(user, deviceCode);
        DeviceConfig.Point existing = device.findPoint(pointCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        long deviceId = requireDeviceId(deviceCode);
        ValidatedPoint validated = normalize(device.getProtocol(), pointCode, cmd, existing);

        try {
            pointAdminStore.update(deviceId, pointCode, validated.command());
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "测点数据不合法");
        }

        // 替换内存对象，Modbus 轮询下一轮就按新地址读
        List<DeviceConfig.Point> points = device.getPoints();
        for (int i = 0; i < points.size(); i++) {
            if (pointCode.equals(points.get(i).getPointCode())) {
                points.set(i, toConfigPoint(validated));
                break;
            }
        }

        // 采集周期可能变了，重算轮询周期；已连接的适配器会判断是否需要重排
        acquisitionService.connect(device);
    }

    // 删除测点：逻辑删除，历史采样数据保留
    public void delete(LoginUser user, String deviceCode, String pointCode) {
        DeviceConfig device = deviceService.get(user, deviceCode);
        if (device.findPoint(pointCode).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        long deviceId = requireDeviceId(deviceCode);

        pointAdminStore.disable(deviceId, pointCode);
        device.getPoints().removeIf(p -> p.getPointCode().equals(pointCode));
        refCache.refresh();

        // 删掉最慢/最快的测点后轮询周期可能变化，交给适配器重算
        acquisitionService.connect(device);
    }

    // 校验并归一化：Modbus 地址解析成寄存器规格，MQTT 保留 JSON 路径
    private ValidatedPoint normalize(Protocol protocol, String pointCode, PointCommand cmd,
                                     DeviceConfig.Point existing) {
        String name = requireText(cmd.name(), "name");
        String address = requireText(cmd.address(), "address");
        String dataType = requireText(cmd.dataType(), "dataType").toLowerCase(Locale.ROOT);
        String unit = (cmd.unit() == null || cmd.unit().isBlank()) ? null : cmd.unit().trim();
        String timeAddress = (cmd.timeAddress() == null || cmd.timeAddress().isBlank())
                ? null : cmd.timeAddress().trim();

        Integer freq = cmd.freqSeconds();
        if (freq != null && freq <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "freqSeconds 必须大于 0");
        }
        if (cmd.minValue() != null && cmd.maxValue() != null && cmd.minValue() > cmd.maxValue()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minValue 不能大于 maxValue");
        }

        // 修改时请求没带的量程沿用旧值，避免只改名称把物理量程清空
        Double rangeMin = cmd.rangeMin() != null ? cmd.rangeMin()
                : (existing == null ? null : existing.getRangeMin());
        Double rangeMax = cmd.rangeMax() != null ? cmd.rangeMax()
                : (existing == null ? null : existing.getRangeMax());
        if (rangeMin != null && rangeMax != null && rangeMin > rangeMax) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rangeMin 不能大于 rangeMax");
        }

        ModbusPointSpec spec = null;
        if (protocol == Protocol.MODBUS) {
            address = address.replaceAll("\\s+", "");
            try {
                spec = ModbusPointSpec.parse(address, dataType);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Modbus 地址无效：" + e.getMessage());
            }
            // data_type 以地址解析结果为准；Modbus 没有设备时间戳
            dataType = spec.dataType().name().toLowerCase(Locale.ROOT);
            timeAddress = null;
        } else if (!address.startsWith("$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "MQTT 地址必须是 JSON 路径（以 $ 开头）");
        }

        PointCommand command = new PointCommand(pointCode, name, address, timeAddress,
                dataType, unit, freq, cmd.minValue(), cmd.maxValue(), rangeMin, rangeMax);
        return new ValidatedPoint(command, spec);
    }

    // 库字段转内存对象，Modbus 解析字段一起填好，轮询可直接使用
    private DeviceConfig.Point toConfigPoint(ValidatedPoint validated) {
        PointCommand command = validated.command();

        DeviceConfig.Point point = new DeviceConfig.Point();
        point.setPointCode(command.pointCode());
        point.setMetric(command.name());
        point.setUnit(command.unit());
        point.setFreq(command.freqSeconds());
        point.setMin(command.minValue());
        point.setMax(command.maxValue());
        point.setRangeMin(command.rangeMin());
        point.setRangeMax(command.rangeMax());

        if (validated.spec() == null) {
            point.setJsonPath(command.address());
            point.setTimeJsonPath(command.timeAddress());
        } else {
            ModbusPointSpec spec = validated.spec();
            point.setAddressText(command.address());
            point.setDataTypeCode(command.dataType());
            point.setRegisterType(spec.registerType());
            point.setAddress(spec.address());
            point.setDataType(spec.dataType());
            point.setScale(spec.scale());
        }
        point.setInvalid(false);
        return point;
    }

    private long requireDeviceId(String deviceCode) {
        Long id = deviceStore.findId(deviceCode);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return id;
    }

    private String requireText(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 不能为空");
        }
        return raw.trim();
    }

    private record ValidatedPoint(PointCommand command, ModbusPointSpec spec) {
    }
}
