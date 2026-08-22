package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.eval.EvaluationService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 过程性评价（功能点 §5/§6）：录入即写穿聚合表；写权限=数据可见（任课教师可评所教班学生） */
@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    @Data
    public static class EvalReq {
        @NotNull(message = "studentId 不能为空")
        private Long studentId;
        @NotNull(message = "indicatorId 不能为空")
        private Long indicatorId;
        @NotBlank(message = "title 不能为空")
        private String title;
        @NotNull(message = "score 不能为空")
        private BigDecimal score;
        private String remark;
        @NotNull(message = "evalTime 不能为空")
        private LocalDateTime evalTime;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> evaluate(@Validated @RequestBody EvalReq req) {
        return ApiResponse.ok(evaluationService.evaluate(AuthUtil.current(), req.getStudentId(),
                req.getIndicatorId(), req.getTitle(), req.getScore(), req.getRemark(), req.getEvalTime()));
    }

    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam Long studentId,
                                                       @RequestParam Long termId) {
        return ApiResponse.ok(evaluationService.list(AuthUtil.current(), studentId, termId));
    }
}
