package com.donghuan.store.jdbc;

import com.donghuan.model.ServiceApplicationCommand;
import com.donghuan.model.ServiceApplicationView;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcServiceApplicationStore implements ServiceApplicationStore {

    private static final String VIEW_SQL = "select a.id, a.application_no, s.code as service_code, " +
            "s.name as service_name, a.tenant_id, t.name as tenant_name, a.applicant_id, " +
            "u.username as applicant_name, a.greenhouse_count, a.contact, a.phone, a.status, " +
            "a.reject_reason, p.id as provision_task_id, p.status as provision_status, " +
            "a.reviewed_at, a.created_at " +
            "from service_application a " +
            "join service_item s on s.id = a.service_item_id " +
            "join tenant t on t.id = a.tenant_id " +
            "join user u on u.id = a.applicant_id " +
            "left join provision_task p on p.application_id = a.id ";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public long insert(long serviceItemId, long tenantId, long applicantId, ServiceApplicationCommand command) {
        String sql = "insert into service_application " +
                "(application_no, service_item_id, tenant_id, applicant_id, greenhouse_count, contact, phone, status) " +
                "values (?, ?, ?, ?, ?, ?, ?, 'PENDING')";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, "T" + System.nanoTime());
            ps.setLong(2, serviceItemId);
            ps.setLong(3, tenantId);
            ps.setLong(4, applicantId);
            ps.setInt(5, command.greenhouseCount());
            ps.setString(6, command.contact().trim());
            ps.setString(7, command.phone());
            return ps;
        }, keyHolder);

        long id = keyHolder.getKey().longValue();
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String applicationNo = "SA" + date + String.format("%04d", id);
        jdbcTemplate.update("update service_application set application_no = ? where id = ?", applicationNo, id);
        return id;
    }

    @Override
    public Optional<ServiceApplicationView> findById(long id) {
        return jdbcTemplate.query(VIEW_SQL + "where a.id = ?", this::mapView, id).stream().findFirst();
    }

    @Override
    public List<ServiceApplicationView> findByApplicant(long applicantId) {
        return jdbcTemplate.query(VIEW_SQL + "where a.applicant_id = ? order by a.created_at desc",
                this::mapView, applicantId);
    }

    @Override
    public List<ServiceApplicationView> findAll(String status) {
        if (status == null || status.isBlank()) {
            return jdbcTemplate.query(VIEW_SQL + "order by a.created_at desc", this::mapView);
        }
        return jdbcTemplate.query(VIEW_SQL + "where a.status = ? order by a.created_at desc",
                this::mapView, status);
    }

    @Override
    @Transactional
    public long approveAndCreateTask(long id, long reviewerId) {
        int updated = jdbcTemplate.update("update service_application " +
                        "set status = 'APPROVED', reviewed_by = ?, reviewed_at = current_timestamp(3) " +
                        "where id = ? and status = 'PENDING'",
                reviewerId, id);
        if (updated == 0) {
            return 0;
        }

        String sql = "insert into provision_task (application_id, status) values (?, 'PENDING')";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, id);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public boolean reject(long id, long reviewerId, String reason) {
        int updated = jdbcTemplate.update("update service_application " +
                        "set status = 'REJECTED', reject_reason = ?, reviewed_by = ?, " +
                        "reviewed_at = current_timestamp(3) where id = ? and status = 'PENDING'",
                reason, reviewerId, id);
        return updated > 0;
    }

    private ServiceApplicationView mapView(ResultSet rs, int rowNum) throws SQLException {
        long taskId = rs.getLong("provision_task_id");
        Long provisionTaskId = rs.wasNull() ? null : taskId;
        return new ServiceApplicationView(
                rs.getLong("id"),
                rs.getString("application_no"),
                rs.getString("service_code"),
                rs.getString("service_name"),
                rs.getLong("tenant_id"),
                rs.getString("tenant_name"),
                rs.getLong("applicant_id"),
                rs.getString("applicant_name"),
                rs.getInt("greenhouse_count"),
                rs.getString("contact"),
                rs.getString("phone"),
                rs.getString("status"),
                rs.getString("reject_reason"),
                provisionTaskId,
                rs.getString("provision_status"),
                instantOrNull(rs, "reviewed_at"),
                instantOrNull(rs, "created_at"));
    }

    private Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
