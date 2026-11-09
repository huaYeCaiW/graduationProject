package com.donghuan.store;

import java.util.Optional;

public interface UserStore {
    record UserRow(long id, String username, String passwordHash,
                   String role, Long tenantId) {
    }

    Optional<UserRow> findEnabledByUsername(String username);
}
