package com.donghuan.store.jdbc;

import com.donghuan.model.DeviceCommand;
import com.donghuan.model.TenantScope;
import com.donghuan.store.DeviceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class JdbcDeviceStore implements DeviceStore {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void insert(DeviceCommand device) {
        String sql = "insert into device " +
                "(device_code, tenant_id, name, protocol, connection_config, enabled) " +
                "values (?, ?, ?, ?, ?, 1)";
        jdbcTemplate.update(sql,
                device.deviceCode(), device.tenantId(), device.name(),
                device.protocol(), device.connectionConfig());
    }

    @Override
    public void update(DeviceCommand device) {
        String sql = "update device set tenant_id = ?, name = ?, protocol = ?, connection_config = ? " +
                "where device_code = ? and enabled = 1";
        jdbcTemplate.update(sql,
                device.tenantId(), device.name(), device.protocol(),
                device.connectionConfig(), device.deviceCode());
    }

    @Override
    @Transactional
    public void disable(String deviceCode) {
        jdbcTemplate.update(
                "update device set enabled = 0 where device_code = ? and enabled = 1",
                deviceCode);
        // 设备下的测点一起停用，历史采样数据不动
        jdbcTemplate.update(
                "update point p join device d on p.device_id = d.id " +
                        "set p.enabled = 0 where d.device_code = ? and p.enabled = 1",
                deviceCode);
    }

    @Override
    public DeviceCommand findDisabled(String deviceCode) {
        List<DeviceCommand> devices = jdbcTemplate.query(
                "select device_code, tenant_id, name, protocol, connection_config " +
                        "from device where device_code = ? and enabled = 0",
                this::mapDisabled,
                deviceCode);
        return devices.isEmpty() ? null : devices.get(0);
    }

    @Override
    public List<DeviceCommand> findDisabledDevices(TenantScope scope) {
        String sql = "select device_code, tenant_id, name, protocol, connection_config " +
                "from device where enabled = 0";
        if (scope.seeAll()) {
            return jdbcTemplate.query(sql + " order by device_code", this::mapDisabled);
        }
        return jdbcTemplate.query(sql + " and tenant_id = ? order by device_code",
                this::mapDisabled, scope.tenantId());
    }

    private DeviceCommand mapDisabled(ResultSet rs, int rowNum) throws SQLException {
        long tenantId = rs.getLong("tenant_id");
        Long tenant = rs.wasNull() ? null : tenantId;
        return new DeviceCommand(
                rs.getString("device_code"), tenant, rs.getString("name"),
                rs.getString("protocol"), rs.getString("connection_config"), false);
    }

    @Override
    @Transactional
    public void restore(DeviceCommand device) {
        jdbcTemplate.update(
                "update device set tenant_id = ?, name = ?, protocol = ?, " +
                        "connection_config = ?, enabled = 1 " +
                        "where device_code = ? and enabled = 0",
                device.tenantId(), device.name(), device.protocol(),
                device.connectionConfig(), device.deviceCode());
        // 删除设备时测点一起停用，恢复时一并启用；历史采样数据不动
        jdbcTemplate.update(
                "update point p join device d on p.device_id = d.id " +
                        "set p.enabled = 1 where d.device_code = ? and p.enabled = 0",
                device.deviceCode());
    }

    @Override
    public Long findId(String deviceCode) {
        List<Long> ids = jdbcTemplate.query(
                "select id from device where device_code = ? and enabled = 1",
                (rs, rowNum) -> rs.getLong("id"),
                deviceCode);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
