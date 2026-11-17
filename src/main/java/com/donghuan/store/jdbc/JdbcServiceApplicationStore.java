package com.donghuan.store.jdbc;

import com.donghuan.model.command.ServiceApplicationCommand;
import com.donghuan.model.view.ServiceApplicationView;
import com.donghuan.store.ServiceApplicationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcServiceApplicationStore implements ServiceApplicationStore {

    private static final DateTimeFormatter NO_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static final String VIEW_SQL = "select a.id, a.application_no, a.tenant_id, " +
            "t.name as tenant_name, a.applicant_id, a.applicant_name, a.greenhouse_name, " +
            "a.phone, a.status, a.created_at, a.reject_reason " +
            "from service_application a " +
            "join tenant t on t.id = a.tenant_id ";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public long insert(long tenantId, long applicantId, ServiceApplicationCommand command) {
        String sql = "insert into service_application " +
                "(application_no, tenant_id, applicant_id, applicant_name, greenhouse_name, phone, status) " +
                "values (?, ?, ?, ?, ?, ?, 'PENDING')";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, "SA" + LocalDateTime.now().format(NO_FORMAT));
            ps.setLong(2, tenantId);
            ps.setLong(3, applicantId);
            ps.setString(4, command.applicantName());
            ps.setString(5, command.greenhouseName());
            ps.setString(6, command.phone());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("申请单写入后未返回主键");
        }
        long applicationId = key.longValue();

        jdbcTemplate.batchUpdate(
                "insert into application_point (application_id, point_type, point_count) values (?, ?, ?)",
                command.points().stream()
                        .map(point -> new Object[]{applicationId, point.type(), point.count()})
                        .toList());
        return applicationId;
    }

    // 管理员批准
    @Override
    public int approve(long id, long reviewerId) {
        return jdbcTemplate.update("update service_application " +
                        "set status = 'APPROVED', reviewed_by = ?, reviewed_at = now(3) " +
                        "where id = ? and status = 'PENDING'", reviewerId, id);
    }

    // 拒绝申请
    @Override
    public int reject(long id, long reviewerId, String reason) {
        return jdbcTemplate.update("update service_application " +
                        "set status = 'REJECTED', reject_reason = ?, reviewed_by = ?, reviewed_at = now(3) " +
                        "where id = ? and status = 'PENDING'",
                reason, reviewerId, id);
    }

    // 安装设备后确认
    @Override
    public int confirm(long id, long reviewerId) {
        return jdbcTemplate.update("update service_application " +
                        "set status = 'INSTALLED', confirmed_by = ?, confirmed_at = now(3) " +
                        "where id = ? and status = 'APPROVED'", reviewerId, id);
    }

    @Override
    public Optional<ServiceApplicationView> findById(long id) {
        return jdbcTemplate.query(VIEW_SQL + "where a.id = ?", this::mapView, id).stream()
                .map(this::withPoints)
                .findFirst();
    }

    @Override
    public List<ServiceApplicationView> findByApplicant(long applicantId) {
        return jdbcTemplate.query(VIEW_SQL + "where a.applicant_id = ? order by a.created_at desc",
                        this::mapView, applicantId).stream()
                .map(this::withPoints)
                .toList();
    }

    @Override
    public List<ServiceApplicationView> findAll(String status) {
        if (status == null || status.isBlank()) {
            return jdbcTemplate.query(VIEW_SQL + "order by a.created_at desc", this::mapView).stream()
                    .map(this::withPoints)
                    .toList();
        }
        return jdbcTemplate.query(VIEW_SQL + "where a.status = ? order by a.created_at desc",
                        this::mapView, status).stream()
                .map(this::withPoints)
                .toList();
    }

    // 需求项单独查，避免在 RowMapper 里再发查询
    private ServiceApplicationView withPoints(ServiceApplicationView application) {
        return new ServiceApplicationView(
                application.id(),
                application.applicationNo(),
                application.tenantId(),
                application.tenantName(),
                application.applicantId(),
                application.applicantName(),
                application.greenhouseName(),
                application.phone(),
                findPoints(application.id()),
                application.status(),
                application.createdAt(),
                application.rejectReason());
    }

    private List<ServiceApplicationView.DemandPointView> findPoints(long applicationId) {
        return jdbcTemplate.query(
                "select point_type, point_count from application_point where application_id = ? order by id",
                (rs, rowNum) -> new ServiceApplicationView.DemandPointView(
                        rs.getString("point_type"), rs.getInt("point_count")),
                applicationId);
    }

    private ServiceApplicationView mapView(ResultSet rs, int rowNum) throws SQLException {
        return new ServiceApplicationView(
                rs.getLong("id"),
                rs.getString("application_no"),
                rs.getLong("tenant_id"),
                rs.getString("tenant_name"),
                rs.getLong("applicant_id"),
                rs.getString("applicant_name"),
                rs.getString("greenhouse_name"),
                rs.getString("phone"),
                List.of(),
                rs.getString("status"),
                instantOrNull(rs, "created_at"),
                rs.getString("reject_reason"));
    }

    private Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
