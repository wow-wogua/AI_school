package com.aischool.server.service.timeline;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.Activity;
import com.aischool.server.entity.ActivitySignup;
import com.aischool.server.entity.Evaluation;
import com.aischool.server.entity.Exam;
import com.aischool.server.entity.Honor;
import com.aischool.server.entity.Score;
import com.aischool.server.entity.Term;
import com.aischool.server.mapper.ActivityMapper;
import com.aischool.server.mapper.ActivitySignupMapper;
import com.aischool.server.mapper.EvaluationMapper;
import com.aischool.server.mapper.ExamMapper;
import com.aischool.server.mapper.HonorMapper;
import com.aischool.server.mapper.ScoreMapper;
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

    public Map<String, Object> events(Long studentId, Long termId) {
        Term term = termMapper.selectById(termId);
        if (term == null) {
            throw new BizException(404, "学期不存在");
        }
        LocalDateTime start = term.getStartDate().atStartOfDay();
        LocalDateTime end = term.getEndDate().atTime(LocalTime.MAX);
        List<Object[]> events = new ArrayList<>();   // [type, time, title, detail]

        // ① 评价
        List<Evaluation> evals = evaluationMapper.selectList(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getStudentId, studentId)
                .ge(Evaluation::getEvalTime, start).le(Evaluation::getEvalTime, end));
        for (Evaluation e : evals) {
            String detail = plain(e.getScore()) + "分"
                    + (isBlank(e.getRemark()) ? "" : "：" + e.getRemark());
            events.add(new Object[]{"评价", e.getEvalTime(), e.getTitle(), detail});
        }

        // ② 活动（时间取 活动开始 → 签到 → 报名 首个非空）
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
            if (time == null || time.isBefore(start) || time.isAfter(end)) {
                continue;
            }
            String detail = !isBlank(su.getAward()) ? "荣获" + su.getAward()
                    : !isBlank(su.getPerformance()) ? su.getPerformance() : "参与活动";
            events.add(new Object[]{"活动", time, a.getTitle(), detail});
        }

        // ③ 荣誉（已确认）
        List<Honor> honors = honorMapper.selectList(new LambdaQueryWrapper<Honor>()
                .eq(Honor::getStudentId, studentId).eq(Honor::getConfirmStatus, "已确认"));
        for (Honor h : honors) {
            if (h.getHonorDate() == null || h.getHonorDate().isBefore(term.getStartDate())
                    || h.getHonorDate().isAfter(term.getEndDate())) {
                continue;
            }
            String detail = Stream.of(h.getLevel(), h.getIssuer())
                    .filter(s -> !isBlank(s)).collect(Collectors.joining("·"));
            events.add(new Object[]{"荣誉", h.getHonorDate().atStartOfDay(), h.getName(),
                    detail.isEmpty() ? "荣誉表彰" : detail});
        }

        // ④ 成绩进步：学期内相邻两次考试总分上升
        List<Exam> exams = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getTermId, termId).orderByAsc(Exam::getExamDate).orderByAsc(Exam::getId));
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

        events.sort(Comparator.comparing((Object[] e) -> (LocalDateTime) e[1]).reversed());
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
        return Map.of("events", rows);
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
