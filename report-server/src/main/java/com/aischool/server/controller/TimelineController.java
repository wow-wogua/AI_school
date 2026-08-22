package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.auth.DataScopeService;
import com.aischool.server.service.timeline.TimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 成长事件时间轴：评价/活动/荣誉/成绩进步统一事件流 */
@RestController
@RequestMapping("/api/timeline")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService timelineService;
    private final DataScopeService dataScope;

    @GetMapping("/{studentId}")
    public ApiResponse<Map<String, Object>> events(@PathVariable Long studentId, @RequestParam Long termId) {
        dataScope.checkStudentAccess(AuthUtil.current(), studentId);
        return ApiResponse.ok(timelineService.events(studentId, termId));
    }
}
