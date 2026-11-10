package com.donghuan.model;


public record TenantScope(boolean seeAll, Long tenantId) {

    public static TenantScope of(LoginUser user) {
        return new TenantScope(user.getRole() == Role.ADMIN || user.getRole() == Role.OPS, user.getTenantId());
    }

    public boolean canSee(Long rowTenantId) {
        if (seeAll) {
            return true;
        }
        return tenantId != null && tenantId.equals(rowTenantId);
    }
}
