package com.donghuan.common;

import com.donghuan.model.Protocol;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@RequiredArgsConstructor
@Component
public class DeviceProperties {

    private final JdbcTemplate jdbcTemplate;
    // 设备配置直接放内存，增删不影响正在遍历的线程
    private final List<DeviceConfig> devices = new CopyOnWriteArrayList<>();

    private static final String DEVICE_SQL = "select d.device_code, " +
            "d.name as device_name, " +
            "d.protocol, " +
            "d.connection_config, " +
            "p.point_code, " +
            "p.name as point_name, " +
            "p.address, " +
            "p.time_address, " +
            "p.data_type, " +
            "p.unit, " +
            "p.freq_seconds, " +
            "p.min_value, " +
            "p.max_value, " +
            "p.range_min, " +
            "p.range_max, " +
            "d.tenant_id " +
            "from device d " +
            "left join point p " +
            "on p.device_id = d.id " +
            "and p.enabled = 1 " +
            "where d.enabled = 1 ";

    // 载入配置：只做数据库到对象的映射，不解析协议字段
    @PostConstruct
    public void load() {
        Map<String, DeviceConfig> next = queryDevices("");

        // 启动装载阶段没有并发读，直接重置同一个列表
        devices.clear();
        devices.addAll(next.values());

        if (next.isEmpty()) {
            log.warn("设备配置为空：device 表无启用设备或 point 表无启用测点，数据将不会接入");
        } else {
            log.info("载入设备配置{}台", next.size());
        }
    }

    // 恢复逻辑删除的设备：只重载这一台，避免重建其他设备对象影响正在运行的 poller
    public Optional<DeviceConfig> loadDevice(String deviceCode) {
        return queryDevices(" and d.device_code = ?", deviceCode).values().stream().findFirst();
    }

    // 按条件装载设备与测点，condition 拼在 where d.enabled = 1 之后
    private Map<String, DeviceConfig> queryDevices(String condition, Object... args) {
        Map<String, DeviceConfig> next = new LinkedHashMap<>();

        jdbcTemplate.query(DEVICE_SQL + condition + " order by d.device_code, p.point_code", rs -> {
            String deviceCode = rs.getString("device_code");
            DeviceConfig device = next.get(deviceCode);

            if (device == null) {
                device = new DeviceConfig();
                device.setDeviceCode(deviceCode);
                device.setName(rs.getString("device_name"));
                Protocol protocol = Protocol.from(rs.getString("protocol"));
                device.setProtocol(protocol);
                long tenantId = rs.getLong("tenant_id");
                device.setTenantId(rs.wasNull() ? null : tenantId);

                // 连接串原样装载，解析交给各协议适配器
                DeviceConfig.Connection connection = new DeviceConfig.Connection();
                connection.setConfig(rs.getString("connection_config"));
                device.setConnection(connection);

                next.put(deviceCode, device);
            }

            String pointCode = rs.getString("point_code");
            if (pointCode == null) {
                return;
            }

            DeviceConfig.Point point = new DeviceConfig.Point();
            point.setPointCode(pointCode);
            point.setMetric(rs.getString("point_name"));
            point.setUnit(rs.getString("unit"));
            int freq = rs.getInt("freq_seconds");
            point.setFreq(rs.wasNull() ? null : freq);

            BigDecimal min = rs.getBigDecimal("min_value");
            BigDecimal max = rs.getBigDecimal("max_value");
            point.setMin(min == null ? null : min.doubleValue());
            point.setMax(max == null ? null : max.doubleValue());

            BigDecimal rangeMin = rs.getBigDecimal("range_min");
            BigDecimal rangeMax = rs.getBigDecimal("range_max");
            point.setRangeMin(rangeMin == null ? null : rangeMin.doubleValue());
            point.setRangeMax(rangeMax == null ? null : rangeMax.doubleValue());

            if (device.getProtocol() == Protocol.MODBUS) {
                point.setAddressText(rs.getString("address"));
                point.setDataTypeCode(rs.getString("data_type"));
            } else {
                point.setJsonPath(rs.getString("address"));
                point.setTimeJsonPath(rs.getString("time_address"));
            }

            device.getPoints().add(point);
        }, args);

        return next;
    }

    // 查设备
    public Optional<DeviceConfig> findDevice(String deviceCode) {
        return devices.stream()
                .filter(d -> d.getDeviceCode().equals(deviceCode))
                .findFirst();
    }

    // 获取所有设备
    public Collection<DeviceConfig> getDevices() {
        return Collections.unmodifiableList(devices);
    }

    // 新增设备：对象是新建的，直接加入内存列表
    public void addDevice(DeviceConfig device) {
        devices.add(device);
    }

    // 删除设备：从内存列表移除，列表是 CopyOnWriteArrayList，遍历中的线程不受影响
    public void removeDevice(String deviceCode) {
        devices.removeIf(d -> d.getDeviceCode().equals(deviceCode));
    }
}
