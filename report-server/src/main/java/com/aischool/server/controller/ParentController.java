package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.ContentItem;
import com.aischool.server.entity.Evaluation;
import com.aischool.server.entity.ParentBinding;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.ContentItemMapper;
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
    private final ContentItemMapper contentMapper;

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

    // ────────────────────────── 内容：通知公告 / 育儿课堂（批2） ──────────────────────────

    /** 已发布内容列表（全校 + 绑定孩子所在班级；按发布时间倒序，最多 50 条） */
    @GetMapping("/contents")
    public ApiResponse<List<Map<String, Object>>> contents(@RequestParam String type) {
        checkParent();
        if (!"NOTICE".equals(type) && !"PARENTING".equals(type)) {
            throw new BizException(400, "type 须为 NOTICE 或 PARENTING");
        }
        List<Long> classIds = boundClassIds();
        List<ContentItem> rows = contentMapper.selectList(new LambdaQueryWrapper<ContentItem>()
                .eq(ContentItem::getType, type)
                .eq(ContentItem::getStatus, 1)
                .and(q -> q.eq(ContentItem::getScope, "ALL")
                        .or().in(!classIds.isEmpty(), ContentItem::getClassId, classIds))
                .orderByDesc(ContentItem::getPublishTime)
                .orderByDesc(ContentItem::getId)
                .last("LIMIT 50"));
        Map<Long, String> classNames = classNamesOf(rows);
        return ApiResponse.ok(rows.stream().map(it -> contentRow(it, classNames, false)).toList());
    }

    /** 内容详情（仅已发布且范围可见；列表卡片点进全文阅读） */
    @GetMapping("/contents/{id}")
    public ApiResponse<Map<String, Object>> contentDetail(@PathVariable Long id) {
        checkParent();
        ContentItem it = contentMapper.selectById(id);
        if (it == null || it.getStatus() == null || it.getStatus() != 1) {
            throw new BizException(404, "内容不存在或未发布");
        }
        if ("CLASS".equals(it.getScope())
                && !boundClassIds().contains(it.getClassId())) {
            throw new BizException(403, "该内容未对您开放");
        }
        Map<Long, String> classNames = classNamesOf(List.of(it));
        return ApiResponse.ok(contentRow(it, classNames, true));
    }

    /** 绑定孩子所在班级（去重；无绑定=空列表） */
    private List<Long> boundClassIds() {
        List<ParentBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<ParentBinding>()
                .eq(ParentBinding::getParentUserId, AuthUtil.current().userId()));
        List<Long> studentIds = bindings.stream().map(ParentBinding::getStudentId).toList();
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return studentMapper.selectBatchIds(studentIds).stream()
                .map(Student::getClassId).filter(c -> c != null).distinct().toList();
    }

    private Map<Long, String> classNamesOf(List<ContentItem> items) {
        List<Long> ids = items.stream().map(ContentItem::getClassId)
                .filter(c -> c != null).distinct().toList();
        return ids.isEmpty() ? Map.of() : clazzMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Clazz::getId, Clazz::getName));
    }

    private Map<String, Object> contentRow(ContentItem it, Map<Long, String> classNames, boolean full) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", it.getId());
        m.put("type", it.getType());
        m.put("title", it.getTitle());
        m.put("coverUrl", it.getCoverUrl());
        m.put("videoUrl", it.getVideoUrl());
        m.put("content", full ? it.getContent() : brief(it.getContent()));
        m.put("scope", it.getScope());
        m.put("className", it.getClassId() == null ? null : classNames.get(it.getClassId()));
        m.put("publishTime", it.getPublishTime() == null ? null : it.getPublishTime().toString());
        return m;
    }

    /** 列表摘要：正文前 80 字（详情接口给全文） */
    private String brief(String content) {
        if (content == null || content.length() <= 80) {
            return content;
        }
        return content.substring(0, 80) + "…";
    }

    private void requireBound(Long studentId) {
        if (bindingMapper.selectCount(new LambdaQueryWrapper<ParentBinding>()
                .eq(ParentBinding::getParentUserId, AuthUtil.current().userId())
                .eq(ParentBinding::getStudentId, studentId)) == 0) {
            throw new BizException(403, "该学生未绑定当前家长账号");
        }
    }
}
