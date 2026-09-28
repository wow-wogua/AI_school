package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/** 免登探活端点（批29）：给 UptimeRobot 等外部探针用，服务器进程挂掉时探针超时即告警 */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    public ApiResponse<Map<String, Object>> ping() {
        return ApiResponse.ok(Map.of("status", "ok", "time", LocalDateTime.now().toString()));
    }
}
