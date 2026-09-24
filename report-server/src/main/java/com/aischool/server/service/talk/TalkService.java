package com.aischool.server.service.talk;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.Talk;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.TalkMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.security.UserPrincipal;
import com.aischool.server.service.auth.DataScopeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 谈心记录（批11，原始需求二行政五件套收口）：教师对可见班级学生（任课/班主任/领导/管理员），
 * 不走审批流；家长不可见（PARENT 无入口+接口拒绝）。
 */
@Service
@RequiredArgsConstructor
public class TalkService {

    private final TalkMapper talkMapper;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final UserMapper userMapper;
    private final DataScopeService dataScope;

    @Data
    public static class TalkReq {
        private Long studentId;
        private String talkDate; // yyyy-MM-dd
        private String talkType;
        private String content;
    }

    public void create(TalkReq req) {
        UserPrincipal user = AuthUtil.current();
        if ("PARENT".equals(user.role())) {
            throw new BizException(403, "家长账号无需谈心记录");
        }
        if (req.getStudentId() == null) {
            throw new BizException(400, "请选择学生");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(req.getTalkDate());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BizException(400, "请填写谈心日期");
        }
        String type = req.getTalkType() == null ? "" : req.getTalkType().trim();
        if (!Talk.TYPES.contains(type)) {
            throw new BizException(400, "谈心类型须为：" + String.join("/", Talk.TYPES));
        }
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new BizException(400, "请填写谈心内容");
        }
        if (req.getContent().length() > 500) {
            throw new BizException(400, "谈心内容不能超过 500 字");
        }
        dataScope.checkStudentAccess(user, req.getStudentId()); // 404/403 数据权限隔离
        Talk t = new Talk();
        t.setTeacherId(user.userId());
        t.setStudentId(req.getStudentId());
        t.setTalkDate(date);
        t.setTalkType(type);
        t.setContent(req.getContent().trim());
        talkMapper.insert(t);
    }

    /** 我的谈心（记录人视角） */
    public List<Map<String, Object>> my() {
        return rows(talkMapper.selectList(new LambdaQueryWrapper<Talk>()
                .eq(Talk::getTeacherId, AuthUtil.current().userId())
                .orderByDesc(Talk::getId)));
    }

    /** 管理端全量（classId 可选筛选） */
    public List<Map<String, Object>> adminList(Long classId) {
        List<Talk> all = talkMapper.selectList(new LambdaQueryWrapper<Talk>().orderByDesc(Talk::getId));
        if (classId != null) {
            Set<Long> stuIds = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                            .eq(Student::getClassId, classId)).stream()
                    .map(Student::getId).collect(Collectors.toSet());
            all = all.stream().filter(t -> stuIds.contains(t.getStudentId())).toList();
        }
        return rows(all);
    }

    private List<Map<String, Object>> rows(List<Talk> talks) {
        if (talks.isEmpty()) {
            return List.of();
        }
        List<Student> stus = studentMapper.selectBatchIds(talks.stream()
                .map(Talk::getStudentId).distinct().toList());
        Map<Long, String> stuNames = stus.stream()
                .collect(Collectors.toMap(Student::getId, Student::getName, (a, b) -> a));
        Map<Long, Long> stuClass = stus.stream()
                .collect(Collectors.toMap(Student::getId, Student::getClassId, (a, b) -> a));
        Map<Long, String> classNames = clazzMapper.selectBatchIds(stuClass.values().stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Clazz::getId, Clazz::getName, (a, b) -> a));
        Map<Long, String> teacherNames = userMapper.selectBatchIds(talks.stream()
                        .map(Talk::getTeacherId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));
        return talks.stream().<Map<String, Object>>map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("studentId", t.getStudentId());
            m.put("studentName", stuNames.getOrDefault(t.getStudentId(), ""));
            Long cid = stuClass.get(t.getStudentId());
            m.put("className", cid == null ? "" : classNames.getOrDefault(cid, ""));
            m.put("teacherName", teacherNames.getOrDefault(t.getTeacherId(), ""));
            m.put("talkDate", t.getTalkDate() == null ? "" : t.getTalkDate().toString());
            m.put("talkType", t.getTalkType());
            m.put("content", t.getContent());
            m.put("createTime", t.getCreateTime());
            return m;
        }).toList();
    }
}
