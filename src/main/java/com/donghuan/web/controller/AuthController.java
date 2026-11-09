package com.donghuan.web.controller;

import com.donghuan.model.LoginUser;
import com.donghuan.store.UserStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserStore userStore;

    public record LoginRequest(String username, String password) {
    }

    @PostMapping("/login")
    public LoginUser login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        if (request.username() == null || request.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名和密码不能为空");
        }
        LoginUser user = userStore.authenticate(request.username(), request.password())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));

        // 防会话固定：登录成功后换一个新的 sessionId 再写入登录态
        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId();
        session.setAttribute(LoginUser.SESSION_KEY, user);
        return user;
    }

    @PostMapping("/logout")
    public void logout(HttpSession session) {
        session.invalidate();
    }

    @GetMapping("/me")
    public LoginUser me(HttpSession session) {
        Object user = session.getAttribute(LoginUser.SESSION_KEY);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return (LoginUser) user;
    }
}
