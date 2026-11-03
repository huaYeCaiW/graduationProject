package com.donghuan.common;

import com.donghuan.model.Protocol;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Component
public class DeviceProperties {

    private final JdbcTemplate jdbcTemplate;
    private volatile Map<String, DeviceConfig> devicesByCode = Map.of();

    // 载入配置
    @PostConstruct
    public void load() {
        Map<String, DeviceConfig> next = new LinkedHashMap<>();
        String sql = "select d.device_code, " +
                        "d.name as device_name, " +
                        "d.protocol, " +
                        "d.connection_config, " +
                        "p.point_code, " +
                        "p.name as point_name, " +
                        "p.address, " +
                        "p.time_address, " +
                        "p.unit, " +
                        "p.freq_seconds, " +
                        "p.min_value, " +
                        "p.max_value " +
                "from device d " +
                "left join point p " +
                        "on p.device_id = d.id " +
                        "and p.enabled = 1 " +
                "where d.enabled = 1 " +
                "order by d.device_code, p.point_code;";

        jdbcTemplate.query(sql, rs -> {
            String deviceCode = rs.getString("device_code");
            DeviceConfig device = next.get(deviceCode);
            if (device == null) {
                device = new DeviceConfig();
                device.setId(deviceCode);
                device.setName(rs.getString("device_name"));
                device.setProtocol(Protocol.from(rs.getString("protocol")));

                DeviceConfig.Connection connection = new DeviceConfig.Connection();
                connection.setTopic(rs.getString("connection_config"));
                device.setConnection(connection);

                next.put(deviceCode, device);
            }

            String pointCode = rs.getString("point_code");
            if (pointCode == null) {
                return;
            }

            DeviceConfig.Point point = new DeviceConfig.Point();
            point.setPointId(pointCode);
            point.setMetric(rs.getString("point_name"));
            point.setUnit(rs.getString("unit"));
            point.setJsonPath(rs.getString("address"));
            point.setTimeJsonPath(rs.getString("time_address"));
            point.setFreq(rs.getString("freq_seconds"));

            BigDecimal min = rs.getBigDecimal("min_value");
            BigDecimal max = rs.getBigDecimal("max_value");
            point.setMin(min == null ? null : min.doubleValue());
            point.setMax(max == null ? null : max.doubleValue());

            device.getPoints().add(point);
        });

        devicesByCode = Collections.unmodifiableMap(next);
        log.debug("载入设备配置{}", next);
    }

    // 查设备
    public Optional<DeviceConfig> findDevice (String deviceId) {
        return Optional.ofNullable(devicesByCode.get(deviceId));
    }

    // 获取所有设备
    public Collection<DeviceConfig> getDevices() {
        return devicesByCode.values();
    }
}
