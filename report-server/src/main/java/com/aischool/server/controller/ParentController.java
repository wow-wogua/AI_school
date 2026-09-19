package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.Evaluation;
import com.aischool.server.entity.ParentBinding;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.EvaluationMapper;
import com.aischool.server.mapper.ParentBindingMapper;
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.AuthUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 家长端（App 内 PARENT 角色分流进入）。
 * 白名单隔离：家长在 DataScopeService 落 default 403，全部教师/管理接口天然不可达，
 * 仅此处按 t_parent_binding 放行绑定孩子的只读信息；成绩相关任何接口不提供（方案A 家长全不可见）。
 */
@RestController
@RequestMapping("/api/parent")
@RequiredArgsConstructor
public class ParentController {

    private final ParentBindingMapper bindingMapper;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final EvaluationMapper evaluationMapper;
    private final UserMapper userMapper;

    private void checkParent() {
        if (!"PARENT".equals(AuthUtil.current().role())) {
            throw new BizException(403, "仅家长账号可访问");
        }
    }

    /** 我的孩子列表（一号可绑多名学生） */
    @GetMapping("/children")
    public ApiResponse<List<Map<String, Object>>> children() {
        checkParent();
        List<ParentBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<ParentBinding>()
                .eq(ParentBinding::getParentUserId, AuthUtil.current().userId())
                .orderByAsc(ParentBinding::getId));
        if (bindings.isEmpty()) {
            return ApiResponse.ok(List.of());
        }
        List<Long> studentIds = bindings.stream().map(ParentBinding::getStudentId).toList();
        Map<Long, Student> students = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, s -> s));
        Map<Long, String> classNames = clazzMapper.selectBatchIds(students.values().stream()
                        .map(Student::getClassId).filter(c -> c != null).distinct().toList()).stream()
                .collect(Collectors.toMap(Clazz::getId, Clazz::getName));
        return ApiResponse.ok(bindings.stream().map(b -> {
            Student s = students.get(b.getStudentId());
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("studentId", b.getStudentId());
            m.put("relation", b.getRelation());
            m.put("name", s == null ? "(学生已删除)" : s.getName());
            m.put("studentNo", s == null ? null : s.getStudentNo());
            m.put("gender", s == null ? null : s.getGender());
            m.put("className", s == null || s.getClassId() == null ? null : classNames.get(s.getClassId()));
            return m;
        }).toList());
    }

    /** 孩子最近评价（行为评价不含成绩；仅绑定孩子可查） */
    @GetMapping("/children/{studentId}/evaluations")
    public ApiResponse<List<Map<String, Object>>> evaluations(@PathVariable Long studentId,
            @RequestParam(defaultValue = "5") int limit) {
        checkParent();
        requireBound(studentId);
        List<Evaluation> rows = evaluationMapper.selectList(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getStudentId, studentId)
                .orderByDesc(Evaluation::getEvalTime)
                .orderByDesc(Evaluation::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 20)));
        Map<Long, String> teacherNames = rows.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(rows.stream().map(Evaluation::getTeacherId)
                        .filter(t -> t != null).distinct().toList()).stream()
                        .collect(Collectors.toMap(User::getId, User::getRealName));
        return ApiResponse.ok(rows.stream().map(e -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("title", e.getTitle());
            m.put("score", e.getScore());
            m.put("remark", e.getRemark());
            m.put("evalTime", e.getEvalTime() == null ? null : e.getEvalTime().toString());
            m.put("teacherName", e.getTeacherId() == null ? null : teacherNames.get(e.getTeacherId()));
            return m;
        }).toList());
    }

    private void requireBound(Long studentId) {
        if (bindingMapper.selectCount(new LambdaQueryWrapper<ParentBinding>()
                .eq(ParentBinding::getParentUserId, AuthUtil.current().userId())
                .eq(ParentBinding::getStudentId, studentId)) == 0) {
            throw new BizException(403, "该学生未绑定当前家长账号");
        }
    }
}
