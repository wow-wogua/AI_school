package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.service.auth.PermissionService;
import com.aischool.server.service.talk.TalkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 谈心记录（批11）：管理端全量 */
@RestController
@RequestMapping("/api/admin/talk")
@RequiredArgsConstructor
public class AdminTalkController {

    private final TalkService talkService;
    private final PermissionService permissionService;

    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) Long classId) {
        permissionService.checkAdminAccess("只有管理员可查谈心记录");
        return ApiResponse.ok(talkService.adminList(classId));
    }
}
