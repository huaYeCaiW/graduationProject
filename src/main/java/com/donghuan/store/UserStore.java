package com.donghuan.store;

import com.donghuan.model.LoginUser;

import java.util.Optional;

public interface UserStore {

    /**
     * 校验用户名密码，成功返回登录用户，失败（含停用、密码错）返回空
     */
    Optional<LoginUser> authenticate(String username, String rawPassword);
}
