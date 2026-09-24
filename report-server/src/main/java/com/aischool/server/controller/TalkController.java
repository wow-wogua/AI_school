package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.service.talk.TalkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 谈心记录（批11）：教师端发起+我的谈心 */
@RestController
@RequestMapping("/api/talk")
@RequiredArgsConstructor
public class TalkController {

    private final TalkService talkService;

    @PostMapping
    public ApiResponse<Void> create(@RequestBody TalkService.TalkReq req) {
        talkService.create(req);
        return ApiResponse.ok();
    }

    @GetMapping("/my")
    public ApiResponse<List<Map<String, Object>>> my() {
        return ApiResponse.ok(talkService.my());
    }
}
