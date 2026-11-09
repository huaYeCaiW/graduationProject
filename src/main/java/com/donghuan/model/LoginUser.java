package com.donghuan.model;

/**
 * 登录态用户，存 Session，不含密码哈希。
 * tenantId 为 null 表示平台账号，可跨租户查看。
 */
public record LoginUser(long id, String username, String role, Long tenantId) {

    public static final String SESSION_KEY = "LOGIN_USER";

    public boolean platform() {
        return tenantId == null;
    }
}
