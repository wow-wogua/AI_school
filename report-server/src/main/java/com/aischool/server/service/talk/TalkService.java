package com.aischool.server.service.talk;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.Notification;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.SysConfig;
import com.aischool.server.entity.Talk;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.SysConfigMapper;
import com.aischool.server.mapper.TalkMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.security.UserPrincipal;
import com.aischool.server.service.auth.DataScopeService;
import com.aischool.server.service.notify.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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
 * 批30 加随访：记录可标「需随访」+到期日，该生再有新谈心即闭环；每日晨间提醒到期未随访（走通知中心）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalkService {

    private final TalkMapper talkMapper;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final UserMapper userMapper;
    private final SysConfigMapper sysConfigMapper;
    private final DataScopeService dataScope;
    private final NotificationService notificationService;

    @Data
    public static class TalkReq {
        private Long studentId;
        private String talkDate; // yyyy-MM-dd
        private String talkType;
        private String content;
        private Boolean followUp;   // 批30：是否需要随访
        private String followDue;   // yyyy-MM-dd，缺省=谈心日期+talk_follow_days（默认14）
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
        // 随访标记（批30）：到期日不早于谈心日、不晚于一年后
        boolean follow = Boolean.TRUE.equals(req.getFollowUp());
        LocalDate due = null;
        if (follow) {
            due = date.plusDays(followDays());
            if (req.getFollowDue() != null && !req.getFollowDue().isBlank()) {
                try {
                    due = LocalDate.parse(req.getFollowDue());
                } catch (DateTimeParseException e) {
                    throw new BizException(400, "随访到期日格式须为 yyyy-MM-dd");
                }
            }
            if (due.isBefore(date)) {
                throw new BizException(400, "随访到期日不能早于谈心日期");
            }
            if (due.isAfter(date.plusYears(1))) {
                throw new BizException(400, "随访到期日不能晚于谈心日期一年后");
            }
        }
        Talk t = new Talk();
        t.setTeacherId(user.userId());
        t.setStudentId(req.getStudentId());
        t.setTalkDate(date);
        t.setTalkType(type);
        t.setContent(req.getContent().trim());
        t.setFollowUp(follow ? Talk.FOLLOW_PENDING : Talk.FOLLOW_NONE);
        t.setFollowDue(due);
        talkMapper.insert(t);
        // 该生再有新谈心=随访闭环：待随访旧记录转已随访（新记录自己若也标了随访则保持待随访）
        if (!follow) {
            talkMapper.selectList(new LambdaQueryWrapper<Talk>()
                            .eq(Talk::getStudentId, req.getStudentId()).eq(Talk::getFollowUp, Talk.FOLLOW_PENDING))
                    .forEach(old -> {
                        old.setFollowUp(Talk.FOLLOW_DONE);
                        talkMapper.updateById(old);
                    });
        }
    }

    private int followDays() {
        try {
            SysConfig c = sysConfigMapper.selectById("talk_follow_days");
            return Math.max(1, Integer.parseInt(c == null || c.getCfgValue() == null ? "14" : c.getCfgValue().trim()));
        } catch (NumberFormatException e) {
            return 14;
        }
    }

    /** 每日 07:50 提醒到期未随访（按教师聚合一条，防多条轰炸）；每日重复提醒直到闭环 */
    @Scheduled(cron = "0 50 7 * * ?")
    public void remindDue() {
        try {
            List<Talk> due = talkMapper.selectList(new LambdaQueryWrapper<Talk>()
                    .eq(Talk::getFollowUp, Talk.FOLLOW_PENDING)
                    .le(Talk::getFollowDue, LocalDate.now()));
            if (due.isEmpty()) {
                return;
            }
            Set<Long> stuIds = due.stream().map(Talk::getStudentId).collect(Collectors.toSet());
            Map<Long, String> stuNames = studentMapper.selectBatchIds(stuIds).stream()
                    .collect(Collectors.toMap(Student::getId, Student::getName, (a, b) -> a));
            due.stream().collect(Collectors.groupingBy(Talk::getTeacherId)).forEach((tid, talks) -> {
                String names = talks.stream().map(t -> stuNames.getOrDefault(t.getStudentId(), "学生#" + t.getStudentId()))
                        .distinct().limit(10).collect(Collectors.joining("、"));
                notificationService.send(tid, Notification.SYSTEM, "随访提醒",
                        "您有 " + talks.size() + " 条谈心随访已到期（" + names + "），请尽快安排随访并补录记录", "/talk");
            });
            log.info("谈心随访提醒：{} 位教师 {} 条到期", due.stream().map(Talk::getTeacherId).distinct().count(), due.size());
        } catch (Exception e) {
            log.warn("谈心随访提醒失败：{}", e.getMessage());
        }
    }

    /** 我的谈心（记录人视角） */
    public List<Map<String, Object>> my() {
        return rows(talkMapper.selectList(new LambdaQueryWrapper<Talk>()
                .eq(Talk::getTeacherId, AuthUtil.current().userId())
                .orderByDesc(Talk::getId)));
    }

    /** 管理端全量（classId/followUp 可选筛选；followUp=1 只看待随访） */
    public List<Map<String, Object>> adminList(Long classId, Integer followUp) {
        List<Talk> all = talkMapper.selectList(new LambdaQueryWrapper<Talk>()
                .eq(followUp != null, Talk::getFollowUp, followUp).orderByDesc(Talk::getId));
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
            m.put("followUp", t.getFollowUp() == null ? 0 : t.getFollowUp());
            m.put("followDue", t.getFollowDue() == null ? "" : t.getFollowDue().toString());
            m.put("createTime", t.getCreateTime());
            return m;
        }).toList();
    }
}
