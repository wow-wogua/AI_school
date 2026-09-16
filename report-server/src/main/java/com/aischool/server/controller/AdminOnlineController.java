package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.OnlineTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 实时在线统计（仅管理员）：管理端顶部统计条轮询 */
@RestController
@RequestMapping("/api/admin/online")
@RequiredArgsConstructor
public class AdminOnlineController {

    private final OnlineTracker onlineTracker;

    @GetMapping
    public ApiResponse<Map<String, Object>> stats() {
        if (!"ADMIN".equals(AuthUtil.current().role())) {
            throw new BizException(403, "只有管理员可查看在线统计");
        }
        var online = onlineTracker.online().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("userId", u.userId());
            m.put("username", u.username());
            m.put("realName", u.realName());
            m.put("lastActiveMs", u.lastActiveMs());
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("onlineCount", online.size());
        data.put("onlineUsers", online);
        data.put("dailyCount", onlineTracker.dailyCount());
        data.put("windowMinutes", OnlineTracker.ONLINE_WINDOW_MIN);
        return ApiResponse.ok(data);
    }
}
