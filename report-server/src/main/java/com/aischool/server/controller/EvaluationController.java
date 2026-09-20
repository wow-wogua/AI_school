package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.Exported;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.eval.EvaluationService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    /** 班级×学期评价导出 xlsx（批6 漏项C2；权限同查看） */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam Long classId, @RequestParam Long termId) {
        Exported f = evaluationService.export(AuthUtil.current(), classId, termId);
        String filename = URLEncoder.encode(f.filename(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(f.content().length)
                .body(f.content());
    }
}
