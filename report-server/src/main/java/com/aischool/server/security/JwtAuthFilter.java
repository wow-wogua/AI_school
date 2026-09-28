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
    private final com.aischool.server.service.OnlineTracker onlineTracker;

    /** 待改密期间放行的端点：改密本身 + 身份信息 + 重新登录 */
    private static final Set<String> MUST_CHANGE_PWD_ALLOW = Set.of(
            "/api/auth/password", "/api/auth/me", "/api/auth/login");

    /** 门卫（批27）放行前缀：请假核验 + 认证 + App 版本更新；其余业务一律 403（前端路由锁之外的服务端兜底） */
    private static boolean guardAllowed(String uri) {
        return uri.startsWith("/api/student-leave") || uri.startsWith("/api/auth") || uri.startsWith("/api/app");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        // 归档导出（批28）用 <a> 直链下载 zip 流，无法带 header：仅此 GET 端点允许 query token 兜底
        if (header == null && "GET".equals(request.getMethod())
                && "/api/admin/archive/export".equals(request.getRequestURI())) {
            String t = request.getParameter("token");
            if (t != null && !t.isBlank()) {
                header = "Bearer " + t;
            }
        }
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
                // 门卫只开放请假核验（9-26 甲方口径：仅查看请假一个功能）
                if ("GUARD".equals(user.role()) && !guardAllowed(request.getRequestURI())) {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getOutputStream().write(objectMapper.writeValueAsBytes(
                            ApiResponse.error(403, "门卫账号仅可使用请假核验功能")));
                    return;
                }
                var auth = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
                SecurityContextHolder.getContext().setAuthentication(auth);
                onlineTracker.touch(user);
            }
        }
        chain.doFilter(request, response);
    }
}
