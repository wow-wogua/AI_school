package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Student;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.ai.AiDraftService;
import com.aischool.server.service.auth.DataScopeService;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** AI 草稿层：只产草稿，硬数字走规则引擎；寄语须教师确认后才进入报告 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiDraftService draftService;
    private final DataScopeService dataScope;

    @Data
    public static class DraftReq {
        @NotNull(message = "studentId 不能为空")
        private Long studentId;
        @NotNull(message = "termId 不能为空")
        private Long termId;
    }

    @Data
    public static class SaveCommentReq {
        @NotNull(message = "studentId 不能为空")
        private Long studentId;
        @NotNull(message = "termId 不能为空")
        private Long termId;
        @NotNull(message = "content 不能为空")
        private String content;
        /** true=确认生效（进入报告），false=仅保存修改 */
        private boolean confirm;
    }

    /** 学业分析（规则引擎，纯硬数字结论） */
    @PostMapping("/analysis")
    public ApiResponse<Map<String, Object>> analysis(@Validated @RequestBody DraftReq req) {
        checkReadable(req.studentId);
        return ApiResponse.ok(draftService.analysis(req.studentId, req.termId));
    }

    /** 班主任寄语草稿（AI/模板生成 → t_comment.ai_draft） */
    @PostMapping("/comment-draft")
    public ApiResponse<Map<String, Object>> commentDraft(@Validated @RequestBody DraftReq req) {
        checkWritable(req.studentId);
        return ApiResponse.ok(draftService.commentDraft(req.studentId, req.termId));
    }

    /** 寄语查看（草稿 + 当前生效内容） */
    @GetMapping("/comment")
    public ApiResponse<Map<String, Object>> comment(@RequestParam Long studentId, @RequestParam Long termId) {
        checkReadable(studentId);
        return ApiResponse.ok(draftService.getComment(studentId, termId));
    }

    /** 寄语保存/确认 */
    @PutMapping("/comment")
    public ApiResponse<Map<String, Object>> saveComment(@Validated @RequestBody SaveCommentReq req) {
        checkWritable(req.studentId);
        return ApiResponse.ok(draftService.saveComment(req.studentId, req.termId, req.content, req.confirm));
    }

    /** 成长总结草稿（不落库，仅展示） */
    @PostMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@Validated @RequestBody DraftReq req) {
        checkReadable(req.studentId);
        return ApiResponse.ok(draftService.summaryDraft(req.studentId, req.termId));
    }

    // ───────────────── 权限 ─────────────────

    /** 读：任一可见该学生的角色（管理员/班主任/任课教师） */
    private void checkReadable(Long studentId) {
        dataScope.checkStudentAccess(AuthUtil.current(), studentId);
    }

    /** 写（生成/保存寄语）：管理员或该班班主任 */
    private void checkWritable(Long studentId) {
        var user = AuthUtil.current();
        Student student = dataScope.checkStudentAccess(user, studentId);
        if (!"ADMIN".equals(user.role())) {
            dataScope.checkClassOperable(user, student.getClassId());
        }
    }
}
