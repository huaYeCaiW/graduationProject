package com.donghuan.store.jdbc;

import com.donghuan.store.UserStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcUserStore implements UserStore {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<UserRow> findEnabledByUsername(String username) {
        List<UserRow> rows = jdbcTemplate.query(
                "select id, username, password_hash, role, tenant_id " +
                    "from user " +
                    "where username = ? and enabled = 1", (rs, i) -> {
                    long id = rs.getLong("id");
                    String name = rs.getString("username");
                    String hash = rs.getString("password_hash");
                    String role = rs.getString("role");
                    Long tenantId = rs.getLong("tenant_id");
                    return new UserRow(id, name, hash, role, tenantId);
                }, username);

        return rows.stream().findFirst();
    }
}
