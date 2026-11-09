package com.donghuan.store.jdbc;

import com.donghuan.model.LoginUser;
import com.donghuan.store.UserStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JdbcUserStore implements UserStore {

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public Optional<LoginUser> authenticate(String username, String rawPassword) {
        String sql = "select id, username, password_hash, role, tenant_id, enabled " +
                "from user where username = ?";

        List<UserRow> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            long rawTenantId = rs.getLong("tenant_id");
            // wasNull 必须紧跟在 getLong 之后，中间再取别的列会失效
            Long tenantId = rs.wasNull() ? null : rawTenantId;
            return new UserRow(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    rs.getString("role"),
                    tenantId,
                    rs.getBoolean("enabled"));
        }, username);

        return rows.stream().findFirst()
                .filter(UserRow::enabled)
                .filter(row -> passwordEncoder.matches(rawPassword, row.passwordHash()))
                .map(row -> new LoginUser(row.id(), row.username(), row.role(), row.tenantId()));
    }

    private record UserRow(long id, String username, String passwordHash,
                           String role, Long tenantId, boolean enabled) {
    }
}
