package com.teampm.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 {@link SecurityContextHolder} 读取当前登录用户；服务层在需强制登录的接口中调用 {@link #requireUser()}。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 未认证或 principal 类型不符时抛出 401 {@link com.teampm.exception.ApiException}。 */
    public static UserPrincipal requireUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof UserPrincipal p)) {
            throw new com.teampm.exception.ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "未登录");
        }
        return p;
    }
}
