package com.aischool.server.service.civility;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.CivilityAward;
import com.aischool.server.entity.CivilityScore;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.Grade;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.CivilityAwardMapper;
import com.aischool.server.mapper.CivilityScoreMapper;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.GradeMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.security.UserPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文明班评比 B 案（批30）：教师对班级按德育规范 12 项打分（扣分/特殊加减），
 * 班级当日分=基础 120+当日合计；排名按 [from,to] 区间自动聚合；
 * 评选=按月冻结排名快照（rank 1-3 即文明班金银铜），每月 1 日自动评选上月，管理端可手动重评。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CivilityService {

    private final CivilityScoreMapper scoreMapper;
    private final CivilityAwardMapper awardMapper;
    private final ClazzMapper clazzMapper;
    private final GradeMapper gradeMapper;
    private final UserMapper userMapper;

    @Data
    public static class ScoreReq {
        private Long classId;
        private String scoreDate; // yyyy-MM-dd
        private Integer sectionNo;
        private String itemText;
        private BigDecimal delta;
        private Integer cnt;
        private String note;
    }

    /** 打分（教师/领导/管理员；家长与门卫无入口）。日期限当日往前 31 天（值日检查基本当天，容错补录） */
    public Map<String, Object> create(ScoreReq req) {
        UserPrincipal user = AuthUtil.current();
        if ("PARENT".equals(user.role()) || "GUARD".equals(user.role())) {
            throw new BizException(403, "仅教师可录入文明班打分");
        }
        if (req.getClassId() == null || clazzMapper.selectById(req.getClassId()) == null) {
            throw new BizException(400, "请选择班级");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(req.getScoreDate());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BizException(400, "请选择打分日期");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new BizException(400, "打分日期不能晚于今天");
        }
        if (date.isBefore(LocalDate.now().minusDays(31))) {
            throw new BizException(400, "只能补录最近 31 天内的打分");
        }
        if (req.getSectionNo() == null || req.getSectionNo() < 1 || req.getSectionNo() > 12) {
            throw new BizException(400, "大项须为 1-12");
        }
        if (req.getItemText() == null || req.getItemText().isBlank()) {
            throw new BizException(400, "请选择具体条目");
        }
        if (req.getItemText().length() > 300) {
            throw new BizException(400, "条目文本过长");
        }
        if (req.getDelta() == null || req.getDelta().compareTo(BigDecimal.ZERO) == 0
                || req.getDelta().abs().compareTo(BigDecimal.valueOf(50)) > 0) {
            throw new BizException(400, "分值须为 -50~50 的非零数");
        }
        int cnt = req.getCnt() == null ? 1 : req.getCnt();
        if (cnt < 1 || cnt > 99) {
            throw new BizException(400, "人次须为 1-99");
        }
        CivilityScore row = new CivilityScore();
        row.setClassId(req.getClassId());
        row.setScoreDate(date);
        row.setSectionNo(req.getSectionNo());
        row.setItemText(req.getItemText().trim());
        row.setDelta(req.getDelta());
        row.setCnt(cnt);
        row.setNote(req.getNote() == null ? null : req.getNote().trim());
        row.setOperatorId(user.userId());
        scoreMapper.insert(row);
        return Map.of("scoreId", row.getId());
    }

    /** 打分记录（classId/dateFrom/dateTo 可选筛选，近 200 条） */
    public List<Map<String, Object>> records(Long classId, LocalDate from, LocalDate to) {
        List<CivilityScore> rows = scoreMapper.selectList(new LambdaQueryWrapper<CivilityScore>()
                .eq(classId != null, CivilityScore::getClassId, classId)
                .ge(from != null, CivilityScore::getScoreDate, from)
                .le(to != null, CivilityScore::getScoreDate, to)
                .orderByDesc(CivilityScore::getId)
                .last("LIMIT 200"));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> classNames = clazzMapper.selectBatchIds(rows.stream()
                        .map(CivilityScore::getClassId).distinct().toList()).stream()
                .collect(Collectors.toMap(Clazz::getId, Clazz::getName, (a, b) -> a));
        List<Long> opIds = rows.stream().map(CivilityScore::getOperatorId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> opNames = opIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(opIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));
        return rows.stream().<Map<String, Object>>map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("classId", r.getClassId());
            m.put("className", classNames.getOrDefault(r.getClassId(), ""));
            m.put("scoreDate", r.getScoreDate().toString());
            m.put("sectionNo", r.getSectionNo());
            m.put("itemText", r.getItemText());
            m.put("delta", r.getDelta());
            m.put("cnt", r.getCnt());
            m.put("note", r.getNote());
            m.put("operatorName", r.getOperatorId() == null ? "" : opNames.getOrDefault(r.getOperatorId(), ""));
            m.put("createTime", r.getCreateTime());
            return m;
        }).toList();
    }

    /** 误录删除（仅管理员/有管理权限的领导） */
    public void delete(Long id) {
        if (scoreMapper.selectById(id) == null) {
            throw new BizException(404, "打分记录不存在");
        }
        scoreMapper.deleteById(id);
    }

    /**
     * 自动汇总排名（[from,to] 含两端）：有检查记录的日子算检查日（当天未被打分的班保底 120），
     * 班级总分=检查日数×120+区间内该班全部 delta×cnt 合计；按年级分组返回名次。
     */
    public Map<String, Object> rank(LocalDate from, LocalDate to, Long gradeId) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new BizException(400, "日期区间不合法");
        }
        List<CivilityScore> rows = scoreMapper.selectList(new LambdaQueryWrapper<CivilityScore>()
                .ge(CivilityScore::getScoreDate, from)
                .le(CivilityScore::getScoreDate, to));
        Set<LocalDate> checkDays = rows.stream().map(CivilityScore::getScoreDate).collect(Collectors.toSet());
        Map<Long, BigDecimal> classDelta = rows.stream().collect(Collectors.toMap(CivilityScore::getClassId,
                r -> r.getDelta().multiply(BigDecimal.valueOf(r.getCnt())), BigDecimal::add));
        List<Clazz> classes = clazzMapper.selectList(null);
        Map<Long, String> gradeNames = gradeMapper.selectList(null).stream()
                .collect(Collectors.toMap(Grade::getId, Grade::getName, (a, b) -> a));
        List<Map<String, Object>> grades = new ArrayList<>();
        Map<Long, List<Clazz>> byGrade = classes.stream()
                .filter(c -> gradeId == null || gradeId.equals(c.getGradeId()))
                .collect(Collectors.groupingBy(Clazz::getGradeId));
        byGrade.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    List<Map<String, Object>> list = e.getValue().stream()
                            .map(c -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("classId", c.getId());
                                m.put("className", c.getName());
                                BigDecimal total = BigDecimal.valueOf(CivilityScore.DAILY_BASE).multiply(BigDecimal.valueOf(checkDays.size()))
                                        .add(classDelta.getOrDefault(c.getId(), BigDecimal.ZERO));
                                m.put("totalScore", total);
                                return m;
                            })
                            .sorted(Comparator.comparing(m -> (BigDecimal) m.get("totalScore"), Comparator.reverseOrder()))
                            .toList();
                    for (int i = 0; i < list.size(); i++) {
                        list.get(i).put("rankNo", i + 1);
                    }
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("gradeId", e.getKey());
                    g.put("gradeName", gradeNames.getOrDefault(e.getKey(), ""));
                    g.put("checkDays", checkDays.size());
                    g.put("classes", list);
                    grades.add(g);
                });
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("from", from.toString());
        data.put("to", to.toString());
        data.put("grades", grades);
        return data;
    }

    /** 评选（按月冻结快照）：rank 1-3=文明班金银铜；重评覆盖同月旧快照 */
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> settle(String month, Long settleBy) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BizException(400, "月份格式须为 yyyy-MM");
        }
        if (ym.isAfter(YearMonth.now())) {
            throw new BizException(400, "不能评选未来月份");
        }
        LocalDate from = ym.atDay(1), to = ym.atEndOfMonth();
        List<Map<String, Object>> grades = (List<Map<String, Object>>) rank(from, to, null).get("grades");
        awardMapper.delete(new LambdaQueryWrapper<CivilityAward>()
                .eq(CivilityAward::getPeriodType, "MONTH").eq(CivilityAward::getPeriodValue, month));
        List<Map<String, Object>> all = new ArrayList<>();
        for (Map<String, Object> g : grades) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> classes = (List<Map<String, Object>>) g.get("classes");
            for (Map<String, Object> c : classes) {
                CivilityAward a = new CivilityAward();
                a.setPeriodType("MONTH");
                a.setPeriodValue(month);
                a.setClassId(((Number) c.get("classId")).longValue());
                a.setGradeId(((Number) g.get("gradeId")).longValue());
                a.setRankNo(((Number) c.get("rankNo")).intValue());
                a.setTotalScore((BigDecimal) c.get("totalScore"));
                a.setSettleTime(LocalDateTime.now().withNano(0));
                a.setSettleBy(settleBy);
                awardMapper.insert(a);
                if (a.getRankNo() <= 3) { // 快照全量存档，文明班=前三
                    Map<String, Object> m = new LinkedHashMap<>(c);
                    m.put("gradeName", g.get("gradeName"));
                    all.add(m);
                }
            }
        }
        return all;
    }

    /** 已评选结果（month 必填；只出文明班前三，全量名次在快照表） */
    public List<Map<String, Object>> awards(String month) {
        List<CivilityAward> rows = awardMapper.selectList(new LambdaQueryWrapper<CivilityAward>()
                .eq(CivilityAward::getPeriodType, "MONTH")
                .eq(month != null, CivilityAward::getPeriodValue, month)
                .orderByDesc(CivilityAward::getPeriodValue)
                .orderByAsc(CivilityAward::getGradeId)
                .orderByAsc(CivilityAward::getRankNo));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> classNames = clazzMapper.selectBatchIds(rows.stream()
                        .map(CivilityAward::getClassId).distinct().toList()).stream()
                .collect(Collectors.toMap(Clazz::getId, Clazz::getName, (a, b) -> a));
        Map<Long, String> gradeNames = gradeMapper.selectBatchIds(rows.stream()
                        .map(CivilityAward::getGradeId).distinct().toList()).stream()
                .collect(Collectors.toMap(Grade::getId, Grade::getName, (a, b) -> a));
        return rows.stream().filter(a -> a.getRankNo() <= 3).<Map<String, Object>>map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("periodValue", a.getPeriodValue());
            m.put("gradeName", gradeNames.getOrDefault(a.getGradeId(), ""));
            m.put("className", classNames.getOrDefault(a.getClassId(), ""));
            m.put("rankNo", a.getRankNo());
            m.put("totalScore", a.getTotalScore());
            return m;
        }).toList();
    }

    /** 每月 1 日 08:10 自动评选上月（无人值守；管理端可手动重评覆盖） */
    @Scheduled(cron = "0 10 8 1 * ?")
    public void autoSettle() {
        try {
            String last = YearMonth.now().minusMonths(1).toString();
            int top = settle(last, null).size();
            log.info("文明班月度自动评选完成：{} 文明班 {} 个", last, top);
        } catch (Exception e) {
            log.warn("文明班月度自动评选失败：{}", e.getMessage());
        }
    }
}
