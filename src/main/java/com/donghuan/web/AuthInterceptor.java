package com.donghuan.web;

import com.donghuan.common.AuthConstants;
import com.donghuan.model.LoginUser;
import com.donghuan.model.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
public class AuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 取 session
        HttpSession session = request.getSession(false);
        // 取 user
        LoginUser user = (session == null) ? null :
                (LoginUser) session.getAttribute(AuthConstants.SESSION_KEY);

        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"未登录\"}");
            return false;
        }

        // 后续 Controller 直接 @RequestAttribute 取，不必再读 session
        request.setAttribute(AuthConstants.REQUEST_USER, user);

        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();

        if ("GET".equals(method)) return true;

        String perm = requiredPermission(method, path);
        if (perm == null) {
            log.warn("写请求未配置权限规则：{} {}", method, path);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"未授权访问\"}");
            return false;
        }

        if (!allowed(perm, user.getRole())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"无权限\"}");
            return false;
        }

        return true;
    }

    // 所需权限
    private String requiredPermission(String method, String path) {
        if ("PUT".equals(method) && path.endsWith("/threshold")) {
            return AuthConstants.PERM_THRESHOLD_WRITE;
        }
        if ("POST".equals(method) && path.equals("/api/devices")) {
            return AuthConstants.PERM_DEVICE_WRITE;
        }
        if (("PUT".equals(method) || "DELETE".equals(method)) && path.startsWith("/api/devices/")) {
            return AuthConstants.PERM_DEVICE_WRITE;
        }
        if (("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))
        && path.startsWith("/api/points/")) {
            return AuthConstants.PERM_POINT_WRITE;
        }
        if (path.startsWith("/api/alarms/") && path.endsWith("/confirm")) {
            return AuthConstants.PERM_ALARM_CONFIRM;
        }
        if (("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))
                && path.startsWith("/api/service-items")) {
            return AuthConstants.PERM_SERVICE_WRITE;
        }
        if ("POST".equals(method) && path.equals("/api/service-applications")) {
            return AuthConstants.PERM_APPLICATION_SUBMIT;
        }
        if ("POST".equals(method) && path.startsWith("/api/service-applications/")
                && (path.endsWith("/approve") || path.endsWith("/reject"))) {
            return AuthConstants.PERM_APPLICATION_REVIEW;
        }
        if ("POST".equals(method) && path.startsWith("/api/provision-tasks/")
                && path.endsWith("/provision")) {
            return AuthConstants.PERM_PROVISION_EXECUTE;
        }
        return null;
    }

    // 判断角色是否满足权限
    private boolean allowed(String perm, Role role) {
        return switch (perm) {
            case AuthConstants.PERM_DEVICE_WRITE,
                 AuthConstants.PERM_POINT_WRITE -> role == Role.ADMIN;
            case AuthConstants.PERM_THRESHOLD_WRITE -> true;
            case AuthConstants.PERM_ALARM_CONFIRM -> role == Role.OPS || role == Role.ADMIN;
            case AuthConstants.PERM_SERVICE_WRITE -> role == Role.ADMIN;
            case AuthConstants.PERM_APPLICATION_SUBMIT -> role == Role.FARMER;
            case AuthConstants.PERM_APPLICATION_REVIEW -> role == Role.ADMIN;
            case AuthConstants.PERM_PROVISION_EXECUTE -> role == Role.OPS || role == Role.ADMIN;
            default -> false;
        };
    }
}
