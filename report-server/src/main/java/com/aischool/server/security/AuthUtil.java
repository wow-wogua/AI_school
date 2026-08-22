package com.aischool.server.security;

import com.aischool.server.common.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 从 SecurityContext 取当前用户的静态工具 */
public final class AuthUtil {

    private AuthUtil() {
    }

    public static UserPrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        throw new BizException(401, "未登录");
    }
}
