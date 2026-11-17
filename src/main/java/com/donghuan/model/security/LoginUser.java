package com.donghuan.model.security;

import com.donghuan.model.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginUser {

    private Long id;
    private String username;
    private Role role;
    private Long tenantId;
}
