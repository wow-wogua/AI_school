package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.Evaluation;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.AiTaskMapper;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.EvaluationMapper;
import com.aischool.server.mapper.LeaderUsageMapper;
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.OnlineTracker;
import com.aischool.server.service.auth.RoleApprovalService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 领导端（App 内 LEADER 角色分流进入）。
 * 全校只读驾驶舱：明细数据（成绩/报告/九维）走既有教师接口——LEADER 在 DataScopeService
 * 可见范围为 null（全可见），写路径被 canEnter/checkClassOperable 拒绝，天然只读。
 */
@RestController
@RequestMapping("/api/leader")
@RequiredArgsConstructor
public class LeaderController {

    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ClazzMapper clazzMapper;
    private final EvaluationMapper evaluationMapper;
    private final OnlineTracker onlineTracker;
    private final RoleApprovalService approvalService;
    private final AiTaskMapper taskMapper;
    private final LeaderUsageMapper usageMapper;

    private void checkLeader() {
        if (!"LEADER".equals(AuthUtil.current().role())) {
            throw new BizException(403, "仅领导账号可访问");
        }
    }

    /** 全校概览：学生/教师/班级规模 + 今日评价 + 实时在线 */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        checkLeader();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("studentCount", studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                .and(q -> q.isNull(Student::getStatus).or().notIn(Student::getStatus, "毕业", "转出"))));
        data.put("teacherCount", userMapper.selectCount(new LambdaQueryWrapper<User>()
                .in(User::getRole, "ADMIN", "LEADER", "HEAD_TEACHER", "TEACHER").eq(User::getStatus, 1)));
        data.put("classCount", clazzMapper.selectCount(null));
        data.put("todayEvalCount", evaluationMapper.selectCount(new LambdaQueryWrapper<Evaluation>()
                .ge(Evaluation::getEvalTime, LocalDate.now().atStartOfDay())));
        data.put("onlineCount", onlineTracker.online().size());
        data.put("dailyActive", onlineTracker.dailyCount());
        // 待我审批（批2-5）：管理员/领导账号双人审批，领导在 App 端处理
        data.put("pendingApprovals", approvalService.pendingCount());
        return ApiResponse.ok(data);
    }

    /** AI 用量统计（复用管理端聚合口径：按日趋势 + 按教师） */
    @GetMapping("/ai-usage")
    public ApiResponse<Map<String, Object>> aiUsage(@RequestParam(defaultValue = "30") int days) {
        checkLeader();
        LocalDateTime since = LocalDateTime.now().minusDays(Math.min(Math.max(days, 1), 365));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("byDay", taskMapper.usageByDay(since));
        data.put("byTeacher", taskMapper.usageByTeacher(since));
        return ApiResponse.ok(data);
    }

    /**
     * 教师使用情况全景（批2）：在职教职工 × 六类行为计数（报告生成/AI tokens/日常评价/成绩录入/微光发布/登录）。
     * 零活跃教师也返回（领导要看谁在用谁没用）；按总活跃降序。
     */
    @GetMapping("/teacher-usage")
    public ApiResponse<Map<String, Object>> teacherUsage(@RequestParam(defaultValue = "30") int days) {
        checkLeader();
        LocalDateTime since = LocalDateTime.now().minusDays(Math.min(Math.max(days, 1), 365));
        List<User> teachers = userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getRole, "ADMIN", "LEADER", "HEAD_TEACHER", "TEACHER")
                .eq(User::getStatus, 1)
                .orderByAsc(User::getId));
        Map<Long, Map<String, Object>> byUser = new java.util.HashMap<>();
        for (User u : teachers) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("teacherId", u.getId());
            row.put("name", u.getRealName());
            row.put("role", u.getRole());
            row.put("reports", 0); row.put("tokens", 0); row.put("evals", 0);
            row.put("scores", 0); row.put("moments", 0); row.put("logins", 0);
            row.put("lastLogin", null);
            byUser.put(u.getId(), row);
        }
        List<Map<String, Object>> ai = usageMapper.aiCount(since);
        merge(byUser, ai, "reports");
        merge(byUser, usageMapper.evalCount(since), "evals");
        merge(byUser, usageMapper.scoreCount(since), "scores");
        merge(byUser, usageMapper.momentCount(since), "moments");
        for (Map<String, Object> r : ai) {   // AI 聚合附带 tokens，一并合并
            Map<String, Object> row = byUser.get(((Number) r.get("userId")).longValue());
            if (row != null) row.put("tokens", ((Number) r.get("tokens")).longValue());
        }
        for (Map<String, Object> r : usageMapper.loginCount(since)) {
            Map<String, Object> row = byUser.get(((Number) r.get("userId")).longValue());
            if (row != null) {
                row.put("logins", ((Number) r.get("cnt")).longValue());
                Object last = r.get("lastLogin");
                row.put("lastLogin", last == null ? null : last.toString());
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>(byUser.values());
        rows.sort((a, b) -> {
            long sa = active(a), sb = active(b);
            return sa != sb ? Long.compare(sb, sa) : String.valueOf(a.get("name")).compareTo(String.valueOf(b.get("name")));
        });
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", days);
        data.put("rows", rows);
        return ApiResponse.ok(data);
    }

    /** 计数字段合并（rows 里 userId 不在教师集的跳过，如离职/家长误入） */
    private void merge(Map<Long, Map<String, Object>> byUser, List<Map<String, Object>> counts, String field) {
        for (Map<String, Object> r : counts) {
            Map<String, Object> row = byUser.get(((Number) r.get("userId")).longValue());
            if (row != null) row.put(field, ((Number) r.get("cnt")).longValue());
        }
    }

    /** 总活跃（排序权重）：报告一次是大动作，权重高些 */
    private long active(Map<String, Object> row) {
        return 3 * L(row.get("reports")) + L(row.get("evals")) + L(row.get("scores"))
                + L(row.get("moments")) + L(row.get("logins"));
    }

    private long L(Object v) {
        return v instanceof Number n ? n.longValue() : 0;
    }
}
