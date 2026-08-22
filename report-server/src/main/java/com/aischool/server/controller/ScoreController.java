package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.score.ScoreService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 成绩管理：考试/录入/Excel 导入/排名（权限见各端点；录入=管理员或该班该科任课教师） */
@RestController
@RequestMapping("/api/score")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;

    @Data
    public static class ExamReq {
        @NotNull(message = "termId 不能为空")
        private Long termId;
        @NotBlank(message = "name 不能为空")
        private String name;
        @NotNull(message = "examDate 不能为空")
        private LocalDate examDate;
        @NotEmpty(message = "subjects 不能为空")
        private List<ScoreService.SubjectReq> subjects;
    }

    @Data
    public static class EntryReq {
        @NotNull(message = "examId 不能为空")
        private Long examId;
        @NotNull(message = "subjectId 不能为空")
        private Long subjectId;
        @NotNull(message = "classId 不能为空")
        private Long classId;
        private List<ScoreService.RowReq> rows;
    }

    /** 建考试（仅管理员） */
    @PostMapping("/exam")
    public ApiResponse<Map<String, Object>> createExam(@Validated @RequestBody ExamReq req) {
        if (!"ADMIN".equals(AuthUtil.current().role())) {
            throw new BizException(403, "只有管理员可创建考试");
        }
        Long id = scoreService.createExam(AuthUtil.current(), req.getTermId(), req.getName(),
                req.getExamDate(), req.getSubjects());
        return ApiResponse.ok(Map.of("examId", id));
    }

    /** 考试列表（任意登录） */
    @GetMapping("/exam/list")
    public ApiResponse<List<Map<String, Object>>> examList() {
        return ApiResponse.ok(scoreService.examList());
    }

    /** 某班在某考试下的可操作科目（任课教师只见所教科目） */
    @GetMapping("/subject-context")
    public ApiResponse<List<Map<String, Object>>> subjectContext(@RequestParam Long examId,
                                                                 @RequestParam Long classId) {
        return ApiResponse.ok(scoreService.subjectContext(AuthUtil.current(), examId, classId));
    }

    /** 某班某科成绩单（名册 + 分数/排名） */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(@RequestParam Long examId,
                                                 @RequestParam Long subjectId,
                                                 @RequestParam Long classId) {
        return ApiResponse.ok(scoreService.listScores(AuthUtil.current(), examId, subjectId, classId));
    }

    /** 批量录入（score=null 清除） */
    @PutMapping("/entry")
    public ApiResponse<Map<String, Object>> entry(@Validated @RequestBody EntryReq req) {
        return ApiResponse.ok(scoreService.entry(AuthUtil.current(), req.getExamId(), req.getSubjectId(),
                req.getClassId(), req.getRows()));
    }

    /** Excel 导入（multipart：file + examId/subjectId/classId） */
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importExcel(@RequestParam Long examId,
                                                        @RequestParam Long subjectId,
                                                        @RequestParam Long classId,
                                                        @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(scoreService.importExcel(AuthUtil.current(), examId, subjectId, classId, file));
    }

    /** 下载导入模板（该班名册预填，.xlsx） */
    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@RequestParam Long classId) {
        byte[] bytes = scoreService.template(AuthUtil.current(), classId);
        String filename = URLEncoder.encode("成绩导入模板.xlsx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(bytes.length)
                .body(bytes);
    }
}
