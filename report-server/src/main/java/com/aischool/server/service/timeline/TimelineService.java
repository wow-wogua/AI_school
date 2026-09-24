package com.aischool.server.service.timeline;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.Activity;
import com.aischool.server.entity.ActivitySignup;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.Evaluation;
import com.aischool.server.entity.Exam;
import com.aischool.server.entity.Honor;
import com.aischool.server.entity.Moment;
import com.aischool.server.entity.MomentStudent;
import com.aischool.server.entity.Report;
import com.aischool.server.entity.Score;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.Term;
import com.aischool.server.mapper.ActivityMapper;
import com.aischool.server.mapper.ActivitySignupMapper;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.EvaluationMapper;
import com.aischool.server.mapper.ExamMapper;
import com.aischool.server.mapper.HonorMapper;
import com.aischool.server.mapper.MomentMapper;
import com.aischool.server.mapper.MomentStudentMapper;
import com.aischool.server.mapper.ReportMapper;
import com.aischool.server.mapper.ScoreMapper;
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.TermMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 事件时间轴：评价 / 活动 / 荣誉 / 成绩进步 统一事件流（纯读聚合） */
@Service
@RequiredArgsConstructor
public class TimelineService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TermMapper termMapper;
    private final EvaluationMapper evaluationMapper;
    private final ActivityMapper activityMapper;
    private final ActivitySignupMapper signupMapper;
    private final HonorMapper honorMapper;
    private final ExamMapper examMapper;
    private final ScoreMapper scoreMapper;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final MomentMapper momentMapper;
    private final MomentStudentMapper momentStudentMapper;
    private final ReportMapper reportMapper;

    public Map<String, Object> events(Long studentId, Long termId) {
        Term term = termMapper.selectById(termId);
        if (term == null) {
            throw new BizException(404, "学期不存在");
        }
        List<Object[]> events = new ArrayList<>();   // [type, time, title, detail]
        LocalDateTime start = term.getStartDate().atStartOfDay();
        LocalDateTime end = term.getEndDate().atTime(LocalTime.MAX);
        collectEvaluations(events, studentId, start, end);
        collectActivities(events, studentId, start, end);
        collectHonors(events, studentId, term.getStartDate(), term.getEndDate());
        collectExamProgress(events, studentId, termId);
        return Map.of("events", toRows(events));
    }

    /** 生命周期档案（原始需求四）：在校全期事件流 + 总览统计（termId 无关，跨全部学期） */
    public Map<String, Object> lifecycle(Long studentId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BizException(404, "学生不存在");
        }
        Clazz clazz = student.getClassId() == null ? null : clazzMapper.selectById(student.getClassId());

        List<Object[]> events = new ArrayList<>();
        collectEvaluations(events, studentId, null, null);
        collectActivities(events, studentId, null, null);
        collectHonors(events, studentId, null, null);
        collectMoments(events, studentId);
        collectExamProgress(events, studentId, null);

        long evaluations = evaluationMapper.selectCount(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getStudentId, studentId));
        long activities = signupMapper.selectCount(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getStudentId, studentId));
        long honors = honorMapper.selectCount(new LambdaQueryWrapper<Honor>()
                .eq(Honor::getStudentId, studentId).eq(Honor::getConfirmStatus, "已确认"));
        long reports = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getStudentId, studentId));
        long moments = events.stream().filter(e -> "微光".equals(e[0])).count();

        Map<String, Object> head = new LinkedHashMap<>();
        head.put("name", student.getName());
        head.put("studentNo", student.getStudentNo());
        head.put("className", clazz == null ? "" : clazz.getName());
        head.put("enrollDate", student.getEnrollDate() == null ? "" : student.getEnrollDate().toString());

        List<Object[]> sorted = events.stream()
                .sorted(Comparator.comparing((Object[] e) -> (LocalDateTime) e[1]).reversed())
                .limit(500).toList();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("evaluations", evaluations);
        stats.put("activities", activities);
        stats.put("honors", honors);
        stats.put("moments", moments);
        stats.put("reports", reports);
        stats.put("events", events.size());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("student", head);
        out.put("stats", stats);
        out.put("truncated", events.size() > 500);
        out.put("events", toRows(sorted));
        return out;
    }

    /** 评价事件；start/end 为 null 时全期 */
    private void collectEvaluations(List<Object[]> events, Long studentId,
                                    LocalDateTime start, LocalDateTime end) {
        List<Evaluation> evals = evaluationMapper.selectList(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getStudentId, studentId)
                .ge(start != null, Evaluation::getEvalTime, start)
                .le(end != null, Evaluation::getEvalTime, end));
        for (Evaluation e : evals) {
            String detail = plain(e.getScore()) + "分"
                    + (isBlank(e.getRemark()) ? "" : "：" + e.getRemark());
            events.add(new Object[]{"评价", e.getEvalTime(), e.getTitle(), detail});
        }
    }

    /** 活动事件（时间取 活动开始 → 签到 → 报名 首个非空）；start/end 为 null 时全期 */
    private void collectActivities(List<Object[]> events, Long studentId,
                                   LocalDateTime start, LocalDateTime end) {
        List<ActivitySignup> signups = signupMapper.selectList(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getStudentId, studentId));
        Map<Long, Activity> acts = signups.isEmpty() ? Map.of()
                : activityMapper.selectBatchIds(signups.stream()
                        .map(ActivitySignup::getActivityId).distinct().toList()).stream()
                        .collect(Collectors.toMap(Activity::getId, Function.identity()));
        for (ActivitySignup su : signups) {
            Activity a = acts.get(su.getActivityId());
            if (a == null) {
                continue;
            }
            LocalDateTime time = a.getStartTime() != null ? a.getStartTime()
                    : su.getCheckinTime() != null ? su.getCheckinTime() : su.getSignupTime();
            if (time == null || (start != null && (time.isBefore(start) || time.isAfter(end)))) {
                continue;
            }
            String detail = !isBlank(su.getAward()) ? "荣获" + su.getAward()
                    : !isBlank(su.getPerformance()) ? su.getPerformance() : "参与活动";
            events.add(new Object[]{"活动", time, a.getTitle(), detail});
        }
    }

    /** 荣誉事件（已确认）；start/end 为 null 时全期 */
    private void collectHonors(List<Object[]> events, Long studentId,
                               java.time.LocalDate start, java.time.LocalDate end) {
        List<Honor> honors = honorMapper.selectList(new LambdaQueryWrapper<Honor>()
                .eq(Honor::getStudentId, studentId).eq(Honor::getConfirmStatus, "已确认"));
        for (Honor h : honors) {
            if (h.getHonorDate() == null
                    || (start != null && (h.getHonorDate().isBefore(start) || h.getHonorDate().isAfter(end)))) {
                continue;
            }
            String detail = Stream.of(h.getLevel(), h.getIssuer())
                    .filter(s -> !isBlank(s)).collect(Collectors.joining("·"));
            events.add(new Object[]{"荣誉", h.getHonorDate().atStartOfDay(), h.getName(),
                    detail.isEmpty() ? "荣誉表彰" : detail});
        }
    }

    /** 微光瞬间（全期；家长上传与教师拍摄均进档案，与家长微光可见口径一致） */
    private void collectMoments(List<Object[]> events, Long studentId) {
        List<Long> momentIds = momentStudentMapper.selectList(new LambdaQueryWrapper<MomentStudent>()
                        .eq(MomentStudent::getStudentId, studentId)).stream()
                .map(MomentStudent::getMomentId).distinct().toList();
        if (momentIds.isEmpty()) {
            return;   // selectBatchIds 空集合会生成 IN () 非法 SQL
        }
        for (Moment m : momentMapper.selectBatchIds(momentIds)) {
            String detail = Stream.of(m.getSceneTag(), m.getNote())
                    .filter(s -> !isBlank(s)).collect(Collectors.joining("·"));
            events.add(new Object[]{"微光", m.getCreateTime(), "微光瞬间",
                    detail.isEmpty() ? "影像记录" : detail});
        }
    }

    /** 成绩进步：相邻两次考试总分上升；termId 为 null 时跨全部考试按日期序 */
    private void collectExamProgress(List<Object[]> events, Long studentId, Long termId) {
        List<Exam> exams = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .eq(termId != null, Exam::getTermId, termId)
                .orderByAsc(Exam::getExamDate).orderByAsc(Exam::getId));
        BigDecimal prevTotal = null;
        for (Exam ex : exams) {
            BigDecimal total = examTotal(studentId, ex.getId());
            if (total == null) {
                continue;   // 该生缺考，与上次可得的考试比较
            }
            if (prevTotal != null && total.compareTo(prevTotal) > 0) {
                events.add(new Object[]{"成绩", ex.getExamDate().atStartOfDay(), "成绩进步",
                        "总分 " + plain(prevTotal) + " → " + plain(total)
                                + "（+" + plain(total.subtract(prevTotal)) + "）"});
            }
            prevTotal = total;
        }
    }

    private List<Map<String, Object>> toRows(List<Object[]> events) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] e : events) {
            LocalDateTime time = (LocalDateTime) e[1];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", e[0]);
            row.put("time", time.toLocalTime().equals(LocalTime.MIDNIGHT)
                    ? time.format(D) : time.format(DT));
            row.put("title", e[2]);
            row.put("detail", e[3]);
            rows.add(row);
        }
        return rows;
    }

    private BigDecimal examTotal(Long studentId, Long examId) {
        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getExamId, examId).eq(Score::getStudentId, studentId));
        return scores.isEmpty() ? null
                : scores.stream().map(Score::getScore)
                        .filter(s -> s != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String plain(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }
}
