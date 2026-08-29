package com.aischool.server.service.feed;

import com.aischool.server.entity.Activity;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.Comment;
import com.aischool.server.entity.Evaluation;
import com.aischool.server.entity.Honor;
import com.aischool.server.entity.Moment;
import com.aischool.server.entity.MomentStudent;
import com.aischool.server.entity.Report;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.Term;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.ActivityMapper;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.CommentMapper;
import com.aischool.server.mapper.EvaluationMapper;
import com.aischool.server.mapper.HonorMapper;
import com.aischool.server.mapper.MomentMapper;
import com.aischool.server.mapper.MomentStudentMapper;
import com.aischool.server.mapper.ReportMapper;
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.TermMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.UserPrincipal;
import com.aischool.server.service.auth.DataScopeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 首页「最近动态」与「成长记录流」（App 化新增）：
 * 把评价 / 荣誉 / 生效寄语 / 活动按时间倒序混排成一条 feed，数据权限与各功能页一致。
 */
@Service
@RequiredArgsConstructor
public class FeedService {

    private final DataScopeService dataScope;
    private final StudentMapper studentMapper;
    private final EvaluationMapper evaluationMapper;
    private final HonorMapper honorMapper;
    private final MomentMapper momentMapper;
    private final MomentStudentMapper momentStudentMapper;
    private final CommentMapper commentMapper;
    private final ActivityMapper activityMapper;
    private final ClazzMapper clazzMapper;
    private final UserMapper userMapper;
    private final ReportMapper reportMapper;
    private final TermMapper termMapper;

    /** 每类单独取的条数上限（合并后再截断，保证时间混排正确） */
    private static final int PER_TYPE_CAP = 30;

    public List<Map<String, Object>> feed(UserPrincipal user, int limit) {
        List<Long> visible = dataScope.visibleClassIds(user);
        List<Student> students = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .in(visible != null, Student::getClassId, visible != null ? visible : List.of(-1L)));
        Map<Long, String> classNames = clazzMapper.selectList(null).stream()
                .collect(Collectors.toMap(Clazz::getId, Clazz::getName, (a, b) -> a));
        Map<Long, Student> stuById = students.stream()
                .collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a));
        Map<Long, String> teacherNames = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getRole, "ADMIN", "HEAD_TEACHER", "TEACHER")).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));
        List<Long> studentIds = students.stream().map(Student::getId).toList();

        List<Map<String, Object>> items = new ArrayList<>();
        if (!studentIds.isEmpty()) {
            for (Evaluation e : evaluationMapper.selectList(new LambdaQueryWrapper<Evaluation>()
                    .in(Evaluation::getStudentId, studentIds)
                    .orderByDesc(Evaluation::getEvalTime)
                    .last("LIMIT " + PER_TYPE_CAP))) {
                items.add(item("评价", stuById.get(e.getStudentId()), classNames,
                        e.getTitle(), e.getRemark(), teacherNames.get(e.getTeacherId()), e.getEvalTime()));
            }
            for (Honor h : honorMapper.selectList(new LambdaQueryWrapper<Honor>()
                    .in(Honor::getStudentId, studentIds)
                    .orderByDesc(Honor::getCreateTime)
                    .last("LIMIT " + PER_TYPE_CAP))) {
                items.add(item("荣誉", stuById.get(h.getStudentId()), classNames,
                        h.getName(), h.getLevel(), null, h.getCreateTime()));
            }
            for (Comment c : commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                    .in(Comment::getStudentId, studentIds)
                    .eq(Comment::getStatus, "生效")
                    .orderByDesc(Comment::getUpdateTime)
                    .last("LIMIT " + PER_TYPE_CAP))) {
                items.add(item("寄语", stuById.get(c.getStudentId()), classNames,
                        "班主任寄语", c.getContent(), null, c.getUpdateTime()));
            }
        }
        // 微光信箱：班级可见范围内的随手拍（照片走 /api/moment/file/{id}）
        List<Moment> moments = momentMapper.selectList(new LambdaQueryWrapper<Moment>()
                .in(visible != null, Moment::getClassId, visible != null ? visible : List.of(-1L))
                .orderByDesc(Moment::getCreateTime)
                .last("LIMIT " + PER_TYPE_CAP));
        if (!moments.isEmpty()) {
            Map<Long, List<MomentStudent>> msByMoment = momentStudentMapper.selectList(
                            new LambdaQueryWrapper<MomentStudent>()
                                    .in(MomentStudent::getMomentId,
                                            moments.stream().map(Moment::getId).toList()))
                    .stream().collect(Collectors.groupingBy(MomentStudent::getMomentId));
            for (Moment m : moments) {
                String names = msByMoment.getOrDefault(m.getId(), List.of()).stream()
                        .map(MomentStudent::getStudentId).map(stuById::get)
                        .filter(s -> s != null).map(Student::getName)
                        .collect(Collectors.joining("、"));
                Map<String, Object> mi = item("微光", null, classNames,
                        m.getSceneTag(), m.getNote(), teacherNames.get(m.getTeacherId()), m.getCreateTime());
                mi.put("momentId", m.getId());
                mi.put("photoUrl", "/api/moment/file/" + m.getId());
                mi.put("studentNames", names);
                items.add(mi);
            }
        }

        for (Activity a : activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .orderByDesc(Activity::getStartTime)
                .last("LIMIT " + PER_TYPE_CAP))) {
            Map<String, Object> m = item("活动", null, classNames,
                    a.getTitle(), a.getIntro(), teacherNames.get(a.getCreatorId()), a.getStartTime());
            m.put("typeLabel", a.getType());
            items.add(m);
        }

        items.sort(Comparator.comparing(m -> (LocalDateTime) m.get("time"), Comparator.reverseOrder()));
        return items.size() > limit ? items.subList(0, limit) : items;
    }

    /** 首页统计卡：在册学生数 / 本学期报告数 / 当前学期名 */
    public Map<String, Object> homeSummary(UserPrincipal user) {
        List<Long> visible = dataScope.visibleClassIds(user);
        Long studentCount = studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                .in(visible != null, Student::getClassId, visible != null ? visible : List.of(-1L)));
        Term current = termMapper.selectList(new LambdaQueryWrapper<Term>()
                .orderByDesc(Term::getIsCurrent).orderByDesc(Term::getId)).stream().findFirst().orElse(null);
        Long reportCount = 0L;
        if (current != null) {
            LambdaQueryWrapper<Report> qw = new LambdaQueryWrapper<Report>().eq(Report::getTermId, current.getId());
            if (visible != null && !visible.isEmpty()) {
                List<Long> studentIds = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                .in(Student::getClassId, visible)).stream().map(Student::getId).toList();
                qw.in(!studentIds.isEmpty(), Report::getStudentId, studentIds.isEmpty() ? List.of(-1L) : studentIds);
            } else if (visible != null) {
                qw.in(Report::getStudentId, List.of(-1L));   // 无可见班级：计 0
            }
            reportCount = reportMapper.selectCount(qw);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("studentCount", studentCount);
        m.put("reportCount", reportCount);
        m.put("termName", current != null ? current.getName() : "");
        return m;
    }

    private Map<String, Object> item(String type, Student stu, Map<Long, String> classNames,
                                     String title, String content, String teacherName,
                                     LocalDateTime time) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("title", title);
        m.put("content", content);
        m.put("teacherName", teacherName);
        m.put("time", time);
        if (stu != null) {
            m.put("studentId", stu.getId());
            m.put("studentName", stu.getName());
            m.put("className", classNames.get(stu.getClassId()));
        }
        return m;
    }
}
