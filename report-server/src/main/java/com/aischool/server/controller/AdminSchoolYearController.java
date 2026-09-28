package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.service.auth.PermissionService;
import com.aischool.server.service.schoolyear.SchoolYearService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 管理端：学年一键滚动（批28）——先预览方案，确认后整事务执行（升级/毕业/新学期一次完成） */
@RestController
@RequestMapping("/api/admin/school-year")
@RequiredArgsConstructor
public class AdminSchoolYearController {

    private final SchoolYearService service;
    private final PermissionService permissionService;

    private void checkAdmin() {
        permissionService.checkAdminAccess("只有管理员可执行学年滚动");
    }

    @GetMapping("/preview")
    public ApiResponse<Map<String, Object>> preview() {
        checkAdmin();
        return ApiResponse.ok(service.preview());
    }

    @Data
    public static class RollReq {
        private String confirmNewYear;
    }

    @PostMapping("/roll")
    public ApiResponse<Map<String, Object>> roll(@RequestBody RollReq req) {
        checkAdmin();
        return ApiResponse.ok(service.roll(req.getConfirmNewYear()));
    }
}
