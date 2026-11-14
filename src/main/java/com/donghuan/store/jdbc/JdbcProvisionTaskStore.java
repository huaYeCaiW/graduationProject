package com.donghuan.store.jdbc;

import com.donghuan.model.GreenhouseCommand;
import com.donghuan.model.ProvisionDeviceView;
import com.donghuan.model.ProvisionPointCommand;
import com.donghuan.model.ProvisionTaskView;
import com.donghuan.model.ServiceApplicationView;
import com.donghuan.model.ServiceItemView;
import com.donghuan.model.ServiceMetricTemplate;
import com.donghuan.store.ProvisionTaskStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class JdbcProvisionTaskStore implements ProvisionTaskStore {

    private static final String TASK_SQL = "select p.id, p.application_id, a.application_no, " +
            "s.id as service_item_id, s.code as service_code, s.name as service_name, " +
            "a.tenant_id, t.name as tenant_name, a.greenhouse_count, p.status, p.remark, " +
            "p.created_at, p.started_at, p.finished_at " +
            "from provision_task p " +
            "join service_application a on a.id = p.application_id " +
            "join service_item s on s.id = a.service_item_id " +
            "join tenant t on t.id = a.tenant_id ";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<ProvisionTaskView> findAll(String status) {
        List<ProvisionTaskView> tasks;
        if (status == null || status.isBlank()) {
            tasks = jdbcTemplate.query(TASK_SQL + "order by p.created_at desc", this::mapTask);
        } else {
            tasks = jdbcTemplate.query(TASK_SQL + "where p.status = ? order by p.created_at desc",
                    this::mapTask, status);
        }
        return tasks;
    }

    @Override
    public Optional<ProvisionTaskView> findById(long id) {
        return jdbcTemplate.query(TASK_SQL + "where p.id = ?", this::mapTask, id).stream()
                .map(task -> new ProvisionTaskView(
                        task.id(), task.applicationId(), task.applicationNo(),
                        task.serviceCode(), task.serviceName(), task.tenantId(), task.tenantName(),
                        task.greenhouseCount(), task.status(), task.remark(), task.createdAt(),
                        task.startedAt(), task.finishedAt(),
                        findMetrics(task.applicationId()), findDevices(task.id())))
                .findFirst();
    }

    @Override
    @Transactional
    public List<String> provision(long taskId, long userId, ServiceApplicationView application,
                                  ServiceItemView item, List<GreenhouseCommand> greenhouses, String remark) {
        int locked = jdbcTemplate.update("update provision_task " +
                        "set status = 'RUNNING', assigned_to = ?, started_at = current_timestamp(3) " +
                        "where id = ? and status = 'PENDING'",
                userId, taskId);
        if (locked == 0) {
            throw new IllegalStateException("开通任务已处理");
        }

        Map<String, ServiceMetricTemplate> templates = item.metrics().stream()
                .collect(Collectors.toMap(ServiceMetricTemplate::metricCode, Function.identity()));

        List<String> deviceCodes = new ArrayList<>();
        for (GreenhouseCommand greenhouse : greenhouses) {
            long deviceId = bindDevice(greenhouse, application);
            insertPoints(deviceId, greenhouse, templates, item);
            snapshotDevice(taskId, deviceId);
            deviceCodes.add(greenhouse.deviceCode());
        }

        jdbcTemplate.update("update service_application set status = 'PROVISIONED' " +
                "where id = ? and status = 'APPROVED'", application.id());
        jdbcTemplate.update("update provision_task set status = 'DONE', finished_at = current_timestamp(3), " +
                "remark = ? where id = ?", remark, taskId);
        return deviceCodes;
    }

    // 已存在设备只绑定租户和申请单，保留原连接配置与测点；新设备按登记信息创建
    private long bindDevice(GreenhouseCommand greenhouse, ServiceApplicationView application) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id, tenant_id, protocol, enabled from device where device_code = ? for update",
                greenhouse.deviceCode());
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.getFirst();
            long deviceId = ((Number) row.get("id")).longValue();
            // tinyint(1) 经 JdbcTemplate 映射后可能是 Boolean，也可能是 Number
            Object enabledValue = row.get("enabled");
            boolean enabled = enabledValue instanceof Boolean flag
                    ? flag : ((Number) enabledValue).intValue() == 1;
            Object tenantId = row.get("tenant_id");
            if (!enabled) {
                throw new IllegalStateException("设备已删除，请先恢复：" + greenhouse.deviceCode());
            }
            if (tenantId != null && ((Number) tenantId).longValue() != application.tenantId()) {
                throw new IllegalStateException("设备已归属其他租户：" + greenhouse.deviceCode());
            }
            // 复用设备必须保持协议一致，否则测点地址格式与设备协议不匹配
            String existingProtocol = (String) row.get("protocol");
            if (existingProtocol == null || !existingProtocol.equalsIgnoreCase(greenhouse.protocol())) {
                throw new IllegalStateException("设备协议不一致：" + greenhouse.deviceCode()
                        + "（已有 " + existingProtocol + "，本次 " + greenhouse.protocol() + "）");
            }
            jdbcTemplate.update("update device set tenant_id = ?, application_id = ? where id = ?",
                    application.tenantId(), application.id(), deviceId);
            return deviceId;
        }

        String sql = "insert into device " +
                "(device_code, tenant_id, application_id, name, protocol, connection_config, enabled) " +
                "values (?, ?, ?, ?, ?, ?, 1)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, greenhouse.deviceCode());
            ps.setLong(2, application.tenantId());
            ps.setLong(3, application.id());
            ps.setString(4, greenhouse.name());
            ps.setString(5, greenhouse.protocol());
            ps.setString(6, greenhouse.connectionConfig());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void insertPoints(long deviceId, GreenhouseCommand greenhouse,
                              Map<String, ServiceMetricTemplate> templates, ServiceItemView item) {
        int existing = jdbcTemplate.queryForObject(
                "select count(*) from point where device_id = ? and enabled = 1", Integer.class, deviceId);

        // 已启用测点跳过；逻辑删除的测点恢复并应用本次登记配置；其余新增
        List<ProvisionPointCommand> restorePoints = new ArrayList<>();
        List<ProvisionPointCommand> newPoints = new ArrayList<>();
        for (ProvisionPointCommand point : greenhouse.points()) {
            List<Boolean> flags = jdbcTemplate.query(
                    "select enabled from point where device_id = ? and point_code = ?",
                    (rs, rowNum) -> rs.getBoolean("enabled"), deviceId, point.metricCode());
            if (flags.isEmpty()) {
                newPoints.add(point);
            } else if (!flags.getFirst()) {
                restorePoints.add(point);
            }
        }
        if (existing + restorePoints.size() + newPoints.size() > item.maxPointsPerGreenhouse()) {
            throw new IllegalStateException("设备测点超过服务上限：" + greenhouse.deviceCode());
        }

        String restoreSql = "update point set name = ?, address = ?, time_address = ?, data_type = ?, " +
                "unit = ?, freq_seconds = ?, min_value = ?, max_value = ?, range_min = ?, range_max = ?, " +
                "enabled = 1 where device_id = ? and point_code = ? and enabled = 0";
        for (ProvisionPointCommand point : restorePoints) {
            ServiceMetricTemplate template = templates.get(point.metricCode());
            jdbcTemplate.update(restoreSql, template.name(), point.address(), point.timeAddress(),
                    point.dataType() == null ? template.dataType() : point.dataType(),
                    template.unit(),
                    point.freqSeconds() == null ? template.freqSeconds() : point.freqSeconds(),
                    point.minValue() == null ? template.defaultMin() : point.minValue(),
                    point.maxValue() == null ? template.defaultMax() : point.maxValue(),
                    point.rangeMin() == null ? template.rangeMin() : point.rangeMin(),
                    point.rangeMax() == null ? template.rangeMax() : point.rangeMax(),
                    deviceId, point.metricCode());
        }

        String sql = "insert into point " +
                "(device_id, point_code, name, address, time_address, data_type, unit, " +
                "freq_seconds, min_value, max_value, range_min, range_max, enabled) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";

        for (ProvisionPointCommand point : newPoints) {
            ServiceMetricTemplate template = templates.get(point.metricCode());
            jdbcTemplate.update(sql, deviceId, point.metricCode(), template.name(),
                    point.address(), point.timeAddress(),
                    point.dataType() == null ? template.dataType() : point.dataType(),
                    template.unit(),
                    point.freqSeconds() == null ? template.freqSeconds() : point.freqSeconds(),
                    point.minValue() == null ? template.defaultMin() : point.minValue(),
                    point.maxValue() == null ? template.defaultMax() : point.maxValue(),
                    point.rangeMin() == null ? template.rangeMin() : point.rangeMin(),
                    point.rangeMax() == null ? template.rangeMax() : point.rangeMax());
        }
    }

    // 记录任务开通时的设备快照，设备后续复用不影响历史任务详情
    private void snapshotDevice(long taskId, long deviceId) {
        Map<String, Object> device = jdbcTemplate.queryForMap(
                "select device_code, name, protocol from device where id = ?", deviceId);
        int pointCount = jdbcTemplate.queryForObject(
                "select count(*) from point where device_id = ? and enabled = 1", Integer.class, deviceId);
        jdbcTemplate.update("insert into provision_task_device " +
                        "(task_id, device_id, device_code, name, protocol, point_count) " +
                        "values (?, ?, ?, ?, ?, ?)",
                taskId, deviceId, device.get("device_code"), device.get("name"),
                device.get("protocol"), pointCount);
    }

    private List<ServiceMetricTemplate> findMetrics(long applicationId) {
        String sql = "select m.metric_code, m.name, m.unit, m.data_type, m.default_min, m.default_max, " +
                "m.range_min, m.range_max, m.freq_seconds " +
                "from service_metric m " +
                "join service_application a on a.service_item_id = m.service_item_id " +
                "where a.id = ? order by m.sort_no, m.id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ServiceMetricTemplate(
                rs.getString("metric_code"), rs.getString("name"), rs.getString("unit"),
                rs.getString("data_type"), doubleOrNull(rs, "default_min"),
                doubleOrNull(rs, "default_max"), doubleOrNull(rs, "range_min"),
                doubleOrNull(rs, "range_max"), intOrNull(rs, "freq_seconds")), applicationId);
    }

    private List<ProvisionDeviceView> findDevices(long taskId) {
        String sql = "select device_code, name, protocol, point_count " +
                "from provision_task_device where task_id = ? order by device_code";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ProvisionDeviceView(
                rs.getString("device_code"), rs.getString("name"),
                rs.getString("protocol"), rs.getInt("point_count")), taskId);
    }

    private ProvisionTaskView mapTask(ResultSet rs, int rowNum) throws SQLException {
        return new ProvisionTaskView(
                rs.getLong("id"),
                rs.getLong("application_id"),
                rs.getString("application_no"),
                rs.getString("service_code"),
                rs.getString("service_name"),
                rs.getLong("tenant_id"),
                rs.getString("tenant_name"),
                rs.getInt("greenhouse_count"),
                rs.getString("status"),
                rs.getString("remark"),
                instantOrNull(rs, "created_at"),
                instantOrNull(rs, "started_at"),
                instantOrNull(rs, "finished_at"),
                List.of(),
                List.of());
    }

    private Double doubleOrNull(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }

    private Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
