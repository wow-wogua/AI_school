package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.service.auth.PermissionService;
import com.aischool.server.service.health.HealthScanService;
import com.aischool.server.service.health.HealthScanService.IssueGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端：数据体检（批8.5；批29 扫描逻辑抽 HealthScanService 与体检周报共用）。
 */
@RestController
@RequestMapping("/api/admin/health")
@RequiredArgsConstructor
public class AdminHealthController {

    private final HealthScanService scanService;
    private final PermissionService permissionService;

    @GetMapping("/scan")
    public ApiResponse<List<IssueGroup>> scan() {
        permissionService.checkAdminAccess("只有管理员可操作系统管理");
        return ApiResponse.ok(scanService.scan());
    }
}
