package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.mapper.AiTaskMapper;
import com.aischool.server.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** AI 用量统计（仅管理员；token 随任务落库，此处按日/按教师聚合） */
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final AiTaskMapper taskMapper;

    @GetMapping("/usage")
    public ApiResponse<Map<String, Object>> usage(@RequestParam(defaultValue = "30") int days) {
        if (!"ADMIN".equals(AuthUtil.current().role())) {
            throw new BizException(403, "只有管理员可查看 AI 用量");
        }
        LocalDateTime since = LocalDateTime.now().minusDays(Math.min(Math.max(days, 1), 365));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("byDay", taskMapper.usageByDay(since));
        data.put("byTeacher", taskMapper.usageByTeacher(since));
        return ApiResponse.ok(data);
    }
}
