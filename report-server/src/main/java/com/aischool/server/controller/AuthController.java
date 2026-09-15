package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.security.AuditFilter;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Data
    public static class LoginReq {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class ChangePwdReq {
        @NotBlank(message = "旧密码不能为空")
        private String oldPassword;
        @NotBlank(message = "新密码不能为空")
        private String newPassword;
    }

    /** 修改自己的密码（登录态）：成功换发新 token（清除待改密态） */
    @PutMapping("/password")
    public ApiResponse<Map<String, Object>> changePassword(@Validated @RequestBody ChangePwdReq req) {
        String token = authService.changePassword(
                AuthUtil.current().userId(), req.getOldPassword(), req.getNewPassword());
        return ApiResponse.ok(Map.of("token", token));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Validated @RequestBody LoginReq req, HttpServletRequest request) {
        Map<String, Object> result = authService.login(req.username, req.password);
        // 认证完成时 SecurityContext 尚无用户，审计行身份会是空——成功后回填给审计过滤器
        request.setAttribute(AuditFilter.ATTR_USER_ID, ((Number) ((Map<?, ?>) result.get("user")).get("id")).longValue());
        request.setAttribute(AuditFilter.ATTR_USERNAME, req.username);
        return ApiResponse.ok(result);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        var user = AuthUtil.current();
        return ApiResponse.ok(Map.of(
                "id", user.userId(), "username", user.username(),
                "realName", user.realName(), "role", user.role(),
                "mustChangePassword", user.mustChangePwd()));
    }
}
