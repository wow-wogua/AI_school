package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.AuditLog;
import com.aischool.server.mapper.AuditLogMapper;
import com.aischool.server.security.AuthUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 审计日志查询（仅管理员；写操作留痕见 AuditFilter） */
@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AuditLogMapper auditLogMapper;

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String username,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size) {
        if (!"ADMIN".equals(AuthUtil.current().role())) {
            throw new BizException(403, "只有管理员可查看审计日志");
        }
        var p = auditLogMapper.selectPage(Page.of(page, Math.min(size, 100)), new LambdaQueryWrapper<AuditLog>()
                .eq(username != null && !username.isBlank(), AuditLog::getUsername, username)
                .and(keyword != null && !keyword.isBlank(),
                        q -> q.like(AuditLog::getUri, keyword).or().like(AuditLog::getBody, keyword))
                .orderByDesc(AuditLog::getId));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", p.getTotal());
        data.put("records", p.getRecords());
        return ApiResponse.ok(data);
    }
}
