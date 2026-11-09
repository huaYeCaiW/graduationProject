package com.donghuan.service;

import com.donghuan.model.LoginUser;
import com.donghuan.store.UserStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserStore userStore;
    private final BCryptPasswordEncoder passwordEncoder;

    public LoginUser login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }

        UserStore.UserRow row = userStore.findEnabledByUsername(username).orElse(null);

        // 用户不存在时也执行一次哈希对比，让响应耗时接近，避免被用来探测用户名是否存在
        String hash = row == null
                ? "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5B6hXKZ0N1n5Gq5r5Uy5q5r5Uy5q5"
                : row.passwordHash();
        boolean matched = passwordEncoder.matches(password, hash);

        if (row == null || !matched) {
            throw new InvalidCredentialsException();
        }

        return new LoginUser(row.id(), row.username(), row.role(), row.tenantId());
    }

    // 用户名或密码错误，由 controller 转为 401
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("用户名或密码错误");
        }
    }
}
