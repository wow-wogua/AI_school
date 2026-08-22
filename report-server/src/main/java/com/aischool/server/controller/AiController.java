package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.AiTask;
import com.aischool.server.entity.Student;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.ai.AiDraftService;
import com.aischool.server.service.ai.AiTaskService;
import com.aischool.server.service.auth.DataScopeService;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** AI 草稿层：只产草稿，硬数字走规则引擎；寄语须教师确认后才进入报告。
 *  同步接口保留（单发即时反馈）；批量/切页场景走 /tasks 任务队列（提交即返，轮询取果）。 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiDraftService draftService;
    private final AiTaskService taskService;
    private final DataScopeService dataScope;
    private final com.fasterxml.jackson.databind.ObjectMapper om;

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

    @Data
    public static class TaskReq {
        /** COMMENT=寄语草稿 / SUMMARY=成长总结 */
        @NotNull(message = "type 不能为空")
        private String type;
        @NotNull(message = "studentId 不能为空")
        private Long studentId;
        @NotNull(message = "termId 不能为空")
        private Long termId;
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

    // ───────────────── 任务队列（切页后台跑 / 多任务并行 / 批量） ─────────────────

    /** 提交任务：入队即返 taskId；同学生同类型未完成的任务自动去重复用 */
    @PostMapping("/tasks")
    public ApiResponse<Map<String, Object>> submitTask(@Validated @RequestBody TaskReq req) {
        checkWritable(req.getStudentId());
        Long id = taskService.submit(req.getType(), req.getStudentId(), req.getTermId(), AuthUtil.current().userId());
        return ApiResponse.ok(Map.of("taskId", id));
    }

    /** 任务详情：排队中附排队位次；成功附解析后的结果体（同同步接口 data） */
    @GetMapping("/tasks/{id}")
    public ApiResponse<Map<String, Object>> task(@PathVariable Long id) {
        AiTask t = taskService.get(id);
        if (t == null) {
            throw new BizException(404, "任务不存在");
        }
        checkReadable(t.getStudentId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", t.getId());
        m.put("type", t.getTaskType());
        m.put("studentId", t.getStudentId());
        m.put("termId", t.getTermId());
        m.put("status", t.getStatus());
        m.put("source", t.getSource());
        m.put("error", t.getError());
        m.put("createTime", t.getCreateTime());
        m.put("startedTime", t.getStartedTime());
        m.put("finishedTime", t.getFinishedTime());
        if ("排队".equals(t.getStatus())) {
            m.put("queuePosition", taskService.queuePosition(t.getId()));
        }
        if (t.getResultJson() != null) {
            try {
                m.put("result", om.readTree(t.getResultJson()));
            } catch (Exception ignore) {
                // 结果体损坏时不阻断状态查询
            }
        }
        return ApiResponse.ok(m);
    }

    /** 我提交的近期任务（全局任务面板数据源，带学生姓名） */
    @GetMapping("/tasks/mine")
    public ApiResponse<Object> myTasks(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(taskService.mine(AuthUtil.current().userId(), limit));
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
