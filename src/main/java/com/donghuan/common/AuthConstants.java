package com.donghuan.common;

public final class AuthConstants {

    public static final String SESSION_KEY = "LOGIN_USER";
    // 拦截器放入 request 域，供 Controller 用 @RequestAttribute 直接取当前用户
    public static final String REQUEST_USER = "REQUEST_LOGIN_USER";

    public static final String PERM_DEVICE_WRITE    = "device:write";
    public static final String PERM_POINT_WRITE    = "point:write";
    public static final String PERM_THRESHOLD_WRITE = "threshold:write";
    public static final String PERM_ALARM_CONFIRM  = "alarm:confirm";
    public static final String PERM_SERVICE_WRITE  = "service:write";
    public static final String PERM_APPLICATION_SUBMIT = "application:submit";
    public static final String PERM_APPLICATION_REVIEW = "application:review";
    public static final String PERM_PROVISION_EXECUTE  = "provision:execute";

    private AuthConstants() {
    }
}
