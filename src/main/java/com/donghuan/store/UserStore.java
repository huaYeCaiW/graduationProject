package com.donghuan.store;

import com.donghuan.model.Role;

import java.util.Optional;

public interface UserStore {
    record UserRow(long id, String username, String passwordHash,
                   Role role, Long tenantId) {
    }

    Optional<UserRow> findEnabledByUsername(String username);
}
