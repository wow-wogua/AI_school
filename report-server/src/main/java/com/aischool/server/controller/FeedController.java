package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.feed.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 首页动态流 / 统计卡（App 化新增）：数据范围沿用角色数据权限 */
@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    /** 最近动态（评价/荣誉/生效寄语/活动 按时间混排） */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> feed(@RequestParam(defaultValue = "20") Integer limit) {
        return ApiResponse.ok(feedService.feed(AuthUtil.current(), Math.min(limit, 50)));
    }

    /** 首页统计：在册学生 / 本学期报告数 / 学期名 */
    @GetMapping("/home-summary")
    public ApiResponse<Map<String, Object>> homeSummary() {
        return ApiResponse.ok(feedService.homeSummary(AuthUtil.current()));
    }
}
