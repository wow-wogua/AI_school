package com.aischool.server.security;

import com.aischool.server.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    /** 待改密期间放行的端点：改密本身 + 身份信息 + 重新登录 */
    private static final Set<String> MUST_CHANGE_PWD_ALLOW = Set.of(
            "/api/auth/password", "/api/auth/me", "/api/auth/login");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserPrincipal user = jwtService.parse(header.substring(7));
            if (user != null) {
                // 首登强制改密：claim 判定（无 DB 查询，不伤并发），未改密前拦下一切业务请求
                if (user.mustChangePwd() && !MUST_CHANGE_PWD_ALLOW.contains(request.getRequestURI())) {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getOutputStream().write(objectMapper.writeValueAsBytes(
                            ApiResponse.error(403, "请先修改初始密码后再操作")));
                    return;
                }
                var auth = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
