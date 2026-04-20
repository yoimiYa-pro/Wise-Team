package com.teampm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 {@code Authorization: Bearer} 头解析访问令牌，校验 {@code type=access} 后载入 {@link UserPrincipal}。
 * 解析失败或令牌类型不符时不强行中断请求链，由后续规则决定匿名/拒绝。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                var claims = jwtTokenProvider.parseAccessToken(token);
                // 刷新令牌与访问令牌共用 Bearer 头时，此处拒绝把 refresh 当 access 用
                if (!"access".equals(claims.get("type"))) {
                    filterChain.doFilter(request, response);
                    return;
                }
                String username = claims.get("username", String.class);
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(username);
                    var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                // 过期/篡改等：清空上下文，避免残留认证信息
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
