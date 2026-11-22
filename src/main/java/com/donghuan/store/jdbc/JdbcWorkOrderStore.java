package com.donghuan.store.jdbc;

import com.donghuan.model.command.WorkOrderCommand;
import com.donghuan.model.view.WorkOrderView;
import com.donghuan.store.WorkOrderStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcWorkOrderStore implements WorkOrderStore {
    private static final DateTimeFormatter NO_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static final String VIEW_SQL = "select w.id, w.order_no, w.tenant_id, t.name as tenant_name, " +
            "w.applicant_id, w.applicant_name, w.greenhouse_name, w.phone, " +
            "w.description, w.status, " +
            "w.accepted_by, ua.username as accepted_by_name, w.accepted_at, " +
            "w.completed_by, uc.username as completed_by_name, w.completed_at, " +
            "w.complete_note, w.confirmed_at, " +
            "w.cancelled_by, ux.username as cancelled_by_name, w.cancelled_at, " +
            "w.cancel_reason, w.created_at " +
            "from work_order w " +
            "join tenant t on t.id = w.tenant_id " +
            "left join user ua on ua.id = w.accepted_by " +
            "left join user uc on uc.id = w.completed_by " +
            "left join user ux on ux.id = w.cancelled_by ";
    private final JdbcTemplate jdbcTemplate;
    @Override
    public long insert(long tenantId, long applicantId, WorkOrderCommand command) {
        String sql = "insert into work_order " +
                "(order_no, " +
                "tenant_id, " +
                "applicant_id, applicant_name, " +
                "greenhouse_name, " +
                "phone, " +
                "description, " +
                "status) " +
                "values (?, ?, ?, ?, ?, ?, ?, 'PENDING')";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, "WO" + LocalDateTime.now().format(NO_FORMAT));
            ps.setLong(2, tenantId);
            ps.setLong(3, applicantId);
            ps.setString(4, command.applicantName());
            ps.setString(5, command.greenhouseName());
            ps.setString(6, command.phone());
            ps.setString(7, command.description());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("工单写入后未返回主键");
        }
        return key.longValue();
    }

    @Override
    public int accept(long id, long opsId) {
        return jdbcTemplate.update("update work_order " +
                "set status = 'IN_PROGRESS', accepted_by = ?, accepted_at = now(3) " +
                "where id = ? and status = 'PENDING'", opsId, id);
    }

    @Override
    public int complete(long id, long opsId, String node) {
        return jdbcTemplate.update("update work_order " +
                "set status = 'WAITING_ACCEPT', completed_at = now(3), " +
                        "completed_by = ?, complete_note = ? " +
                        "where id = ? and status = 'IN_PROGRESS'",
                opsId, node, id);
    }

    @Override
    public int confirm(long id) {
        return jdbcTemplate.update("update work_order " +
                "set status = 'DONE', confirmed_at = now(3) " +
                "where id = ? and status = 'WAITING_ACCEPT'", id);
    }

    @Override
    public int cancelByFarmer(long id, long userId, String reason) {
        return jdbcTemplate.update("update work_order " +
                "set status = 'CANCELLED', cancelled_at = now(3), " +
                "cancelled_by = ?, cancel_reason = ? " +
                "where status = 'PENDING' and id = ?",
                userId, reason, id);
    }

    @Override
    public int cancelByAdmin(long id, long userId, String reason) {
        return jdbcTemplate.update(
                "update work_order set status = 'CANCELLED', cancelled_by = ?, " +
                        "cancelled_at = now(3), cancel_reason = ? " +
                        "where id = ? and status in ('PENDING', 'IN_PROGRESS')",
                userId, reason, id);
    }

    @Override
    public Optional<WorkOrderView> findById(long id) {
        return jdbcTemplate.query(VIEW_SQL + "where w.id = ?", this::mapView, id).stream()
                .findFirst();
    }

    @Override
    public List<WorkOrderView> findByApplicant(long applicantId) {
        return jdbcTemplate.query(VIEW_SQL +
                "where applicant_id = ? " +
                "order by w.created_at desc, w.id desc",
                this::mapView, applicantId);
    }

    @Override
    public List<WorkOrderView> findAll(String status) {
        if (status == null || status.isBlank()) {
            return jdbcTemplate.query(VIEW_SQL + "order by w.created_at desc, w.id desc", this::mapView);

        }
        return jdbcTemplate.query(VIEW_SQL + "where w.status = ? " +
                "order by w.created_at desc, w.id desc",
                this::mapView, status);

    }

    private WorkOrderView mapView(ResultSet rs, int rowNum) throws SQLException {
        return new WorkOrderView(
                rs.getLong("id"),
                rs.getString("order_no"),
                rs.getLong("tenant_id"),
                rs.getString("tenant_name"),
                rs.getLong("applicant_id"),
                rs.getString("applicant_name"),
                rs.getString("greenhouse_name"),
                rs.getString("phone"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getObject("accepted_by", Long.class),
                rs.getString("accepted_by_name"),
                instantOrNull(rs, "accepted_at"),
                rs.getObject("completed_by", Long.class),
                rs.getString("completed_by_name"),
                instantOrNull(rs, "completed_at"),
                rs.getString("complete_note"),
                instantOrNull(rs, "confirmed_at"),
                rs.getObject("cancelled_by", Long.class),
                rs.getString("cancelled_by_name"),
                instantOrNull(rs, "cancelled_at"),
                rs.getString("cancel_reason"),
                instantOrNull(rs, "created_at")

        );
    }
    private Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
