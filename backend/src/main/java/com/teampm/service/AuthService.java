package com.teampm.service;

import com.teampm.exception.ApiException;
import com.teampm.mapper.UserMapper;
import com.teampm.security.JwtTokenProvider;
import com.teampm.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 认证：用户名密码走 {@link AuthenticationManager}，成功后签发 access/refresh；刷新接口校验 refresh 专用 claims。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    public Map<String, Object> login(String username, String password) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            UserPrincipal p = (UserPrincipal) auth.getPrincipal();
            String access = jwtTokenProvider.createAccessToken(p.getId(), p.getUsername(), p.getRole());
            String refresh = jwtTokenProvider.createRefreshToken(p.getId());
            return Map.of(
                    "accessToken", access,
                    "refreshToken", refresh,
                    "role", p.getRole(),
                    "userId", p.getId(),
                    "username", p.getUsername()
            );
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
    }

    public Map<String, Object> refresh(String refreshToken) {
        try {
            var claims = jwtTokenProvider.parseRefreshToken(refreshToken);
            if (!"refresh".equals(claims.get("type"))) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "无效令牌");
            }
            Long userId = Long.parseLong(claims.getSubject());
            var u = userMapper.findById(userId);
            if (u == null || u.getStatus() == null || u.getStatus() == 0) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "用户无效");
            }
            String access = jwtTokenProvider.createAccessToken(u.getId(), u.getUsername(), u.getRole());
            String refresh = jwtTokenProvider.createRefreshToken(u.getId());
            return Map.of("accessToken", access, "refreshToken", refresh);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "刷新失败");
        }
    }
}
