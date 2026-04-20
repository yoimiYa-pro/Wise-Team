package com.teampm.web;

import com.teampm.domain.User;
import com.teampm.mapper.UserMapper;
import com.teampm.security.UserPrincipal;
import com.teampm.service.AuthService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录、刷新令牌、当前用户资料；除 {@code /me} 外路径在 {@link com.teampm.security.SecurityConfig} 中匿名放行。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserMapper userMapper;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginReq req) {
        return authService.login(req.getUsername(), req.getPassword());
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(@RequestBody RefreshReq req) {
        return authService.refresh(req.getRefreshToken());
    }

    @GetMapping("/me")
    public User me(@AuthenticationPrincipal UserPrincipal principal) {
        User u = userMapper.findById(principal.getId());
        if (u != null) {
            u.setPasswordHash(null);
        }
        return u;
    }

    @Data
    public static class LoginReq {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    @Data
    public static class RefreshReq {
        @NotBlank
        private String refreshToken;
    }
}
