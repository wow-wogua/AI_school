package com.aischool.server.service.score;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.Exam;
import com.aischool.server.entity.ExamSubject;
import com.aischool.server.entity.Score;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.Subject;
import com.aischool.server.entity.Teach;
import com.aischool.server.entity.Term;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.ExamMapper;
import com.aischool.server.mapper.ExamSubjectMapper;
import com.aischool.server.mapper.ScoreMapper;
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.SubjectMapper;
import com.aischool.server.mapper.TeachMapper;
import com.aischool.server.mapper.TermMapper;
import com.aischool.server.security.UserPrincipal;
import com.aischool.server.service.auth.DataScopeService;
import com.aischool.server.service.excel.ExcelScoreHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成绩管理：考试（含科目满分）→ 按任课关系录入 → 全体竞争排名（同分同名次）→ 回填单科/总分最高分。
 * 排名纯 Java 内存计算，零自定义 SQL；报告契约不读排名列（种子全 NULL），重算零契约风险。
 */
@Service
@RequiredArgsConstructor
public class ScoreService {

    private final ExamMapper examMapper;
    private final ExamSubjectMapper examSubjectMapper;
    private final ScoreMapper scoreMapper;
    private final StudentMapper studentMapper;
    private final SubjectMapper subjectMapper;
    private final TeachMapper teachMapper;
    private final TermMapper termMapper;
    private final ClazzMapper clazzMapper;
    private final DataScopeService dataScope;
    private final ExcelScoreHelper excel;

    // ───────────────── 考试 ─────────────────

    /** 建考试（管理员）：一次带齐科目与满分 */
    public Long createExam(UserPrincipal user, Long termId, String name, LocalDate examDate,
                           List<SubjectReq> subjects) {
        Term term = termMapper.selectById(termId);
        if (term == null) {
            throw new BizException(404, "学期不存在");
        }
        if (examDate == null || examDate.isBefore(term.getStartDate()) || examDate.isAfter(term.getEndDate())) {
            throw new BizException(400, "考试日期必须落在学期起止日期内");
        }
        if (subjects == null || subjects.isEmpty()) {
            throw new BizException(400, "至少设置一个考试科目");
        }
        Exam exam = new Exam();
        exam.setTermId(termId);
        exam.setName(name);
        exam.setExamDate(examDate);
        examMapper.insert(exam);
        for (SubjectReq s : subjects) {
            if (subjectMapper.selectById(s.getSubjectId()) == null) {
                throw new BizException(404, "学科不存在: " + s.getSubjectId());
            }
            if (s.getFullScore() == null || s.getFullScore().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(400, "满分必须大于 0");
            }
            if (examSubjectMapper.selectCount(new LambdaQueryWrapper<ExamSubject>()
                    .eq(ExamSubject::getExamId, exam.getId())
                    .eq(ExamSubject::getSubjectId, s.getSubjectId())) > 0) {
                throw new BizException(400, "考试科目重复: " + s.getSubjectId());
            }
            ExamSubject es = new ExamSubject();
            es.setExamId(exam.getId());
            es.setSubjectId(s.getSubjectId());
            es.setFullScore(s.getFullScore());
            examSubjectMapper.insert(es);
        }
        return exam.getId();
    }

    /** 考试列表（附学期名与科目数） */
    public List<Map<String, Object>> examList() {
        List<Exam> exams = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .orderByDesc(Exam::getExamDate).orderByDesc(Exam::getId));
        Map<Long, String> termNames = termMapper.selectList(null).stream()
                .collect(Collectors.toMap(Term::getId, Term::getName));
        Map<Long, Long> subjectCounts = examSubjectMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(ExamSubject::getExamId, Collectors.counting()));
        return exams.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("termId", e.getTermId());
            m.put("termName", termNames.get(e.getTermId()));
            m.put("name", e.getName());
            m.put("examDate", e.getExamDate());
            m.put("classMaxTotal", e.getClassMaxTotal());
            m.put("gradeMaxTotal", e.getGradeMaxTotal());
            m.put("subjectCount", subjectCounts.getOrDefault(e.getId(), 0L));
            return m;
        }).toList();
    }

    // ───────────────── 录入上下文 ─────────────────

    /** 某班在某考试下可操作的科目（任课教师只见所教科目；管理员全量） */
    public List<Map<String, Object>> subjectContext(UserPrincipal user, Long examId, Long classId) {
        checkClassVisible(user, classId);
        requireExam(examId);
        Map<Long, String> subjectNames = subjectMapper.selectList(null).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName));
        return examSubjectMapper.selectList(new LambdaQueryWrapper<ExamSubject>()
                        .eq(ExamSubject::getExamId, examId).orderByAsc(ExamSubject::getSubjectId))
                .stream().map(es -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("subjectId", es.getSubjectId());
                    m.put("name", subjectNames.get(es.getSubjectId()));
                    m.put("fullScore", es.getFullScore());
                    m.put("classMax", es.getClassMax());
                    m.put("gradeMax", es.getGradeMax());
                    m.put("editable", canEnter(user, classId, es.getSubjectId()));
                    return m;
                }).toList();
    }

    /** 某班某科成绩单（名册 + 分数/排名；缺分为 null） */
    public Map<String, Object> listScores(UserPrincipal user, Long examId, Long subjectId, Long classId) {
        checkClassVisible(user, classId);
        ExamSubject es = requireExamSubject(examId, subjectId);
        List<Student> roster = roster(classId);
        Map<Long, Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                        .eq(Score::getExamId, examId).eq(Score::getSubjectId, subjectId))
                .stream().collect(Collectors.toMap(Score::getStudentId, Function.identity(), (a, b) -> a));
        List<Map<String, Object>> rows = roster.stream().map(st -> {
            Score sc = scores.get(st.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("studentId", st.getId());
            m.put("studentNo", st.getStudentNo());
            m.put("name", st.getName());
            m.put("score", sc == null ? null : sc.getScore());
            m.put("classRank", sc == null ? null : sc.getClassRank());
            m.put("gradeRank", sc == null ? null : sc.getGradeRank());
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fullScore", es.getFullScore());
        data.put("editable", canEnter(user, classId, subjectId));
        data.put("rows", rows);
        return data;
    }

    // ───────────────── 录入 / 导入 ─────────────────

    /** 批量录入：rows 中 score=null 表示清除该生该科成绩 */
    public Map<String, Object> entry(UserPrincipal user, Long examId, Long subjectId, Long classId,
                                     List<RowReq> rows) {
        checkEnterable(user, classId, subjectId);
        ExamSubject es = requireExamSubject(examId, subjectId);
        Map<Long, Student> roster = roster(classId).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        Map<Long, Score> existing = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                        .eq(Score::getExamId, examId).eq(Score::getSubjectId, subjectId))
                .stream().collect(Collectors.toMap(Score::getStudentId, Function.identity(), (a, b) -> a));
        if (rows == null) {
            rows = List.of();
        }
        int saved = 0;
        for (RowReq r : rows) {
            if (r.getStudentId() == null || !roster.containsKey(r.getStudentId())) {
                throw new BizException(400, "学生不在该班级: " + r.getStudentId());
            }
            Score old = existing.get(r.getStudentId());
            if (r.getScore() == null) {
                if (old != null) {
                    scoreMapper.deleteById(old.getId());
                }
                continue;
            }
            if (r.getScore().compareTo(BigDecimal.ZERO) < 0 || r.getScore().compareTo(es.getFullScore()) > 0) {
                throw new BizException(400, roster.get(r.getStudentId()).getName()
                        + " 的成绩超出 [0, " + es.getFullScore() + "]");
            }
            if (old != null) {
                old.setScore(r.getScore());
                old.setCreatedBy(user.userId());
                scoreMapper.updateById(old);
            } else {
                Score sc = new Score();
                sc.setExamId(examId);
                sc.setSubjectId(subjectId);
                sc.setStudentId(r.getStudentId());
                sc.setScore(r.getScore());
                sc.setCreatedBy(user.userId());
                scoreMapper.insert(sc);
            }
            saved++;
        }
        recompute(examId);
        return Map.of("saved", saved);
    }

    /** Excel 导入：按班内学号匹配，逐行 skip 原因 */
    public Map<String, Object> importExcel(UserPrincipal user, Long examId, Long subjectId, Long classId,
                                           MultipartFile file) {
        checkEnterable(user, classId, subjectId);
        ExamSubject es = requireExamSubject(examId, subjectId);
        List<ExcelScoreHelper.ScoreRow> parsed;
        try (var in = file.getInputStream()) {
            parsed = excel.read(in);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(400, "读取文件失败: " + e.getMessage());
        }
        Map<String, Student> byNo = roster(classId).stream()
                .collect(Collectors.toMap(Student::getStudentNo, Function.identity(), (a, b) -> a));
        List<RowReq> rows = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (ExcelScoreHelper.ScoreRow r : parsed) {
            Student st = byNo.get(r.studentNo());
            if (st == null) {
                skipped.add(Map.of("studentNo", r.studentNo(), "name", r.name(), "reason", "学号不在该班级"));
                continue;
            }
            if (r.score() == null) {
                continue; // 成绩留空 = 不动该生
            }
            if (r.score().compareTo(BigDecimal.ZERO) < 0 || r.score().compareTo(es.getFullScore()) > 0) {
                skipped.add(Map.of("studentNo", r.studentNo(), "name", r.name(),
                        "reason", "超出 [0, " + es.getFullScore() + "]"));
                continue;
            }
            rows.add(new RowReq(st.getId(), r.score()));
        }
        Map<String, Object> result = new LinkedHashMap<>(entry(user, examId, subjectId, classId, rows));
        result.put("skipped", skipped);
        return result;
    }

    /** 下载导入模板（该班名册预填） */
    public byte[] template(UserPrincipal user, Long classId) {
        checkClassVisible(user, classId);
        return excel.template(roster(classId));
    }

    // ───────────────── 排名与最高分 ─────────────────

    /**
     * 重算一次考试的全体排名与最高分（每次录入后调用，幂等）。
     * 竞争排名：同分同名次（1,2,2,4）；class_rank 限班内、grade_rank 全体。
     * 单列结构：class_max/grade_max（单科）、class_max_total/grade_max_total（总分）均取全体最高，口径见架构 M7 节。
     */
    void recompute(Long examId) {
        List<ExamSubject> subjects = examSubjectMapper.selectList(new LambdaQueryWrapper<ExamSubject>()
                .eq(ExamSubject::getExamId, examId));
        List<Score> all = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getExamId, examId).isNotNull(Score::getScore));
        Map<Long, Long> classOf = studentMapper.selectList(null).stream()
                .collect(Collectors.toMap(Student::getId, Student::getClassId, (a, b) -> a));

        for (ExamSubject es : subjects) {
            List<Score> rows = all.stream().filter(s -> s.getSubjectId().equals(es.getSubjectId())).toList();
            assignRanks(rows, classOf);
            rows.forEach(scoreMapper::updateById);
            BigDecimal max = rows.stream().map(Score::getScore).max(Comparator.naturalOrder()).orElse(null);
            if (!eq(max, es.getClassMax()) || !eq(max, es.getGradeMax())) {
                es.setClassMax(max);
                es.setGradeMax(max);
                examSubjectMapper.updateById(es);
            }
        }

        // 总分：每生在该考试全部科目上的得分之和（未录科目按缺分计），最高分回填考试
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        List<Long> subjectIds = subjects.stream().map(ExamSubject::getSubjectId).toList();
        for (Score s : all) {
            if (subjectIds.contains(s.getSubjectId())) {
                totals.merge(s.getStudentId(), s.getScore(), BigDecimal::add);
            }
        }
        BigDecimal maxTotal = totals.values().stream().max(Comparator.naturalOrder()).orElse(null);
        Exam exam = examMapper.selectById(examId);
        if (exam != null && (!eq(maxTotal, exam.getClassMaxTotal()) || !eq(maxTotal, exam.getGradeMaxTotal()))) {
            exam.setClassMaxTotal(maxTotal);
            exam.setGradeMaxTotal(maxTotal);
            examMapper.updateById(exam);
        }
    }

    /** 竞争排名：先全体后班内；同分同名次 */
    private void assignRanks(List<Score> rows, Map<Long, Long> classOf) {
        List<Score> sorted = rows.stream()
                .sorted(Comparator.comparing(Score::getScore).reversed()
                        .thenComparing(Score::getStudentId))
                .toList();
        assign(sorted, Score::setGradeRank);
        Map<Long, List<Score>> byClass = sorted.stream()
                .collect(Collectors.groupingBy(s -> classOf.getOrDefault(s.getStudentId(), -1L),
                        LinkedHashMap::new, Collectors.toList()));
        byClass.values().forEach(classRows -> assign(classRows, Score::setClassRank));
    }

    private void assign(List<Score> sorted, java.util.function.BiConsumer<Score, Integer> setter) {
        int rank = 0;
        BigDecimal prev = null;
        for (int i = 0; i < sorted.size(); i++) {
            Score s = sorted.get(i);
            if (prev == null || s.getScore().compareTo(prev) != 0) {
                rank = i + 1;
                prev = s.getScore();
            }
            setter.accept(s, rank);
        }
    }

    // ───────────────── 权限与查询小件 ─────────────────

    /** 成绩录入：管理员或该班该科的任课教师（班主任非任课不可录，功能点 §1） */
    private void checkEnterable(UserPrincipal user, Long classId, Long subjectId) {
        if (!canEnter(user, classId, subjectId)) {
            throw new BizException(403, "只有管理员或该班该科的任课教师可录入成绩");
        }
    }

    private boolean canEnter(UserPrincipal user, Long classId, Long subjectId) {
        if ("ADMIN".equals(user.role())) {
            return true;
        }
        return teachMapper.selectCount(new LambdaQueryWrapper<Teach>()
                .eq(Teach::getTeacherId, user.userId())
                .eq(Teach::getClassId, classId)
                .eq(Teach::getSubjectId, subjectId)) > 0;
    }

    private void checkClassVisible(UserPrincipal user, Long classId) {
        if (clazzMapper.selectById(classId) == null) {
            throw new BizException(404, "班级不存在");
        }
        List<Long> visible = dataScope.visibleClassIds(user);
        if (visible != null && !visible.contains(classId)) {
            throw new BizException(403, "无权访问该班级（数据权限隔离）");
        }
    }

    private List<Student> roster(Long classId) {
        return studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, classId).orderByAsc(Student::getStudentNo));
    }

    private Exam requireExam(Long examId) {
        Exam e = examMapper.selectById(examId);
        if (e == null) {
            throw new BizException(404, "考试不存在");
        }
        return e;
    }

    private ExamSubject requireExamSubject(Long examId, Long subjectId) {
        ExamSubject es = examSubjectMapper.selectList(new LambdaQueryWrapper<ExamSubject>()
                .eq(ExamSubject::getExamId, examId).eq(ExamSubject::getSubjectId, subjectId))
                .stream().findFirst().orElse(null);
        if (es == null) {
            throw new BizException(404, "该考试未设置此科目");
        }
        return es;
    }

    private static boolean eq(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? a == b : a.compareTo(b) == 0;
    }

    // ───────────────── DTO ─────────────────

    @lombok.Data
    public static class SubjectReq {
        private Long subjectId;
        private BigDecimal fullScore;
    }

    @lombok.Data
    public static class RowReq {
        private Long studentId;
        private BigDecimal score;

        public RowReq() {}

        public RowReq(Long studentId, BigDecimal score) {
            this.studentId = studentId;
            this.score = score;
        }
    }
}
