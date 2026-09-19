package com.aischool.server.service.auth;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.UserPermission;
import com.aischool.server.mapper.UserPermissionMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.security.UserPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 权限点（角色之外按人叠加授权，V10 t_user_permission）。
 * 批1 码表：ADMIN_ACCESS = 管理员级配置权（授予领导即开放管理端操作）。
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    public static final String PERM_ADMIN_ACCESS = "ADMIN_ACCESS";

    private final UserPermissionMapper userPermissionMapper;

    /** 是否具有管理端配置权：ADMIN 角色，或被授予 ADMIN_ACCESS 权限点（如需管系统的领导） */
    public boolean hasAdminAccess(UserPrincipal user) {
        if ("ADMIN".equals(user.role())) {
            return true;
        }
        return userPermissionMapper.selectCount(new LambdaQueryWrapper<UserPermission>()
                .eq(UserPermission::getUserId, user.userId())
                .eq(UserPermission::getPermCode, PERM_ADMIN_ACCESS)) > 0;
    }

    /** 管理端操作统一入口（替代各控制器手写的 checkAdmin 字面量判断） */
    public void checkAdminAccess() {
        checkAdminAccess("只有管理员可操作系统管理");
    }

    public void checkAdminAccess(String message) {
        if (!hasAdminAccess(AuthUtil.current())) {
            throw new BizException(403, message);
        }
    }
}
