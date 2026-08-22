package com.aischool.server.security;

/** 认证后的当前用户（来自 JWT claims） */
public record UserPrincipal(Long userId, String username, String realName, String role) {
}
