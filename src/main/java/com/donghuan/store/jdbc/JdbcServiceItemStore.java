package com.donghuan.store.jdbc;

import com.donghuan.model.ServiceItemCommand;
import com.donghuan.model.ServiceItemView;
import com.donghuan.model.ServiceMetricTemplate;
import com.donghuan.store.ServiceItemStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcServiceItemStore implements ServiceItemStore {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<ServiceItemView> findAll(boolean includeDisabled) {
        String sql = "select id, code, name, description, max_greenhouses, max_points_per_greenhouse, enabled " +
                "from service_item";
        if (includeDisabled) {
            return jdbcTemplate.query(sql + " order by id", this::mapItemWithMetrics);
        }
        return jdbcTemplate.query(sql + " where enabled = 1 order by id", this::mapItemWithMetrics);
    }

    @Override
    public Optional<ServiceItemView> findByCode(String code) {
        String sql = "select id, code, name, description, max_greenhouses, max_points_per_greenhouse, enabled " +
                "from service_item where code = ?";
        return jdbcTemplate.query(sql, this::mapItemWithMetrics, code).stream().findFirst();
    }

    @Override
    @Transactional
    public long insert(ServiceItemCommand command) {
        String sql = "insert into service_item " +
                "(code, name, description, max_greenhouses, max_points_per_greenhouse, enabled) " +
                "values (?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, command.code().trim());
            ps.setString(2, command.name().trim());
            ps.setString(3, command.description());
            ps.setInt(4, command.maxGreenhouses());
            ps.setInt(5, command.maxPointsPerGreenhouse());
            ps.setBoolean(6, command.enabled() == null || command.enabled());
            return ps;
        }, keyHolder);

        long id = keyHolder.getKey().longValue();
        insertMetrics(id, command.metrics());
        return id;
    }

    @Override
    @Transactional
    public void update(long id, ServiceItemCommand command) {
        String sql = "update service_item set name = ?, description = ?, max_greenhouses = ?, " +
                "max_points_per_greenhouse = ?, enabled = ? where id = ?";
        jdbcTemplate.update(sql, command.name().trim(), command.description(),
                command.maxGreenhouses(), command.maxPointsPerGreenhouse(),
                command.enabled() == null || command.enabled(), id);

        jdbcTemplate.update("delete from service_metric where service_item_id = ?", id);
        insertMetrics(id, command.metrics());
    }

    @Override
    public void disable(long id) {
        jdbcTemplate.update("update service_item set enabled = 0 where id = ?", id);
    }

    private void insertMetrics(long itemId, List<ServiceMetricTemplate> metrics) {
        String sql = "insert into service_metric " +
                "(service_item_id, metric_code, name, unit, data_type, default_min, default_max, " +
                "range_min, range_max, freq_seconds, sort_no) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        for (int i = 0; i < metrics.size(); i++) {
            ServiceMetricTemplate metric = metrics.get(i);
            jdbcTemplate.update(sql, itemId, metric.metricCode().trim(), metric.name().trim(),
                    metric.unit(), metric.dataType(), metric.defaultMin(), metric.defaultMax(),
                    metric.rangeMin(), metric.rangeMax(), metric.freqSeconds(), i + 1);
        }
    }

    private ServiceItemView mapItemWithMetrics(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        long id = rs.getLong("id");
        return new ServiceItemView(
                id,
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("max_greenhouses"),
                rs.getInt("max_points_per_greenhouse"),
                rs.getBoolean("enabled"),
                findMetrics(id));
    }

    private List<ServiceMetricTemplate> findMetrics(long itemId) {
        String sql = "select metric_code, name, unit, data_type, default_min, default_max, " +
                "range_min, range_max, freq_seconds from service_metric " +
                "where service_item_id = ? order by sort_no, id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ServiceMetricTemplate(
                rs.getString("metric_code"),
                rs.getString("name"),
                rs.getString("unit"),
                rs.getString("data_type"),
                doubleOrNull(rs, "default_min"),
                doubleOrNull(rs, "default_max"),
                doubleOrNull(rs, "range_min"),
                doubleOrNull(rs, "range_max"),
                intOrNull(rs, "freq_seconds")), itemId);
    }

    private Double doubleOrNull(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }

    private Integer intOrNull(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
