package com.teampm.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserPrincipal requireUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof UserPrincipal p)) {
            throw new com.teampm.exception.ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "未登录");
        }
        return p;
    }
}
