package com.donghuan.store.jdbc;

import com.donghuan.model.AlarmStatus;
import com.donghuan.model.AlarmValue;
import com.donghuan.model.LimitType;
import com.donghuan.model.TenantScope;
import com.donghuan.store.AlarmStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcAlarmStore implements AlarmStore {
    private final JdbcTemplate jdbcTemplate;

    private static AlarmValue mapRow(ResultSet rs, int rowNum) {
        try {
            return getAlarmValue(rs);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    private static AlarmValue getAlarmValue(ResultSet rs) throws SQLException {
        AlarmValue av = new AlarmValue();
        av.setId(rs.getLong("id"));
        av.setPointId(rs.getLong("point_id"));
        av.setValue(rs.getDouble("value"));
        AlarmStatus status = AlarmStatus.valueOf(rs.getString("status"));
        av.setStatus(status);
        av.setThreshold(rs.getDouble("threshold"));
        av.setLimitType(LimitType.valueOf(rs.getString("limit_type")));
        av.setSampleAt(rs.getTimestamp("sample_at").toInstant());
        av.setCreateAt(rs.getTimestamp("created_at").toInstant());
        av.setConfirmedBy(rs.getObject("confirmed_by", Long.class));
        av.setMaintainedBy(rs.getObject("maintained_by", Long.class));

        Timestamp confirmedAt = rs.getTimestamp("confirmed_at");
        av.setConfirmedAt(confirmedAt == null ? null : confirmedAt.toInstant());

        Timestamp recoveredAt = rs.getTimestamp("recovered_at");
        av.setRecoveredAt(recoveredAt == null ? null : recoveredAt.toInstant());

        return av;
    }

    // 警告初次发生插入alarm
    @Override
    public long insert(AlarmValue alarm) {
        String sql = "insert into alarm " +
                "(point_id, value, threshold, limit_type, status, sample_at) " +
                "values (?, ?, ?, ?, ?, ?) ";
        // keyHolder可在插入时获取主键id
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, alarm.getPointId());
            ps.setDouble(2, alarm.getValue());
            ps.setDouble(3, alarm.getThreshold());
            ps.setString(4, alarm.getLimitType().name());
            ps.setString(5, alarm.getStatus().name());
            ps.setTimestamp(6, Timestamp.from(alarm.getSampleAt()));
            return ps;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey(), "插入后未返回主键").longValue();
    }
    // 警告持续发生更新当前警告值
    @Override
    public void touch(long id, double value) {
        jdbcTemplate.update("update alarm set value = ? where id = ?",
                value, id);
    }

    // 数值回复正常
    @Override
    public void markRecovered(long id, Instant recoveredAt) {
        jdbcTemplate.update("update alarm set status = ?, recovered_at = ? where id = ? and status = ?",
                AlarmStatus.RECOVERED.name(), Timestamp.from(recoveredAt),
                id, AlarmStatus.ACTIVE.name());

    }

    // 数值持续异常手动确认
    @Override
    public boolean confirm(TenantScope scope, long alarmId, long userId, Instant confirmedAt) {
        if (!scope.seeAll()) {
            return false;
        }
        return jdbcTemplate.update("update alarm " +
                "set confirmed_by = ?, confirmed_at = ? " +
                "where id = ? and status = ?",
                userId, Timestamp.from(confirmedAt), alarmId, AlarmStatus.ACTIVE.name()) > 0;
    }

    // 系统使用
    @Override
    public List<AlarmValue> findAllActive() {

        return jdbcTemplate.query("select " +
                        "id, point_id, value, threshold, " +
                        "limit_type, sample_at, " +
                        "created_at, confirmed_by, confirmed_at, " +
                        "recovered_at, maintained_by, status " +
                        "from alarm where status = ?",
                JdbcAlarmStore::mapRow, AlarmStatus.ACTIVE.name());
    }

    @Override
    public List<AlarmValue> findActive(TenantScope scope) {
        if (!scope.seeAll()) {
            return jdbcTemplate.query("select " +
                            "a.id, a.point_id, a.value, a.threshold, " +
                            "a.limit_type, a.sample_at, " +
                            "a.created_at, a.confirmed_by, a.confirmed_at, " +
                            "a.recovered_at, a.maintained_by, a.status " +
                            "from alarm a join point p " +
                            "on a.point_id = p.id " +
                            "join device d " +
                            "on p.device_id = d.id " +
                            "where a.status = ? and d.tenant_id = ?",
                    JdbcAlarmStore::mapRow, AlarmStatus.ACTIVE.name(), scope.tenantId());
        }
        return findAllActive();
    }

    @Override
    public List<AlarmValue> findAll(TenantScope scope) {
        if (!scope.seeAll()) {
            return jdbcTemplate.query("select " +
                            "a.id, a.point_id, a.value, a.threshold, " +
                            "a.limit_type, a.sample_at, " +
                            "a.created_at, a.confirmed_by, a.confirmed_at, " +
                            "a.recovered_at, a.maintained_by, a.status " +
                            "from alarm a join point p " +
                            "on a.point_id = p.id " +
                            "join device d " +
                            "on p.device_id = d.id " +
                            "where d.tenant_id = ? " +
                            "order by a.id desc",
                    JdbcAlarmStore::mapRow, scope.tenantId());
        }
        return jdbcTemplate.query("select " +
                        "id, point_id, value, threshold, " +
                        "limit_type, sample_at, " +
                        "created_at, confirmed_by, confirmed_at, " +
                        "recovered_at, status, maintained_by " +
                        "from alarm order by id desc",
                JdbcAlarmStore::mapRow);
    }
}
