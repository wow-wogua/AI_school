package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.auth.AuthService;
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

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Validated @RequestBody LoginReq req) {
        return ApiResponse.ok(authService.login(req.username, req.password));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        var user = AuthUtil.current();
        return ApiResponse.ok(Map.of(
                "id", user.userId(), "username", user.username(),
                "realName", user.realName(), "role", user.role()));
    }
}
