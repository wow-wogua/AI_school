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
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.OnlineTracker;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
    private final AiTaskMapper taskMapper;

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
}
