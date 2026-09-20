package com.aischool.server.service.score;

import com.aischool.server.common.BizException;
import com.aischool.server.common.Exported;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.Exam;
import com.aischool.server.entity.ExamSubject;
import com.aischool.server.entity.Score;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.Subject;
import com.aischool.server.entity.Teach;
import com.aischool.server.entity.TeacherProfile;
import com.aischool.server.entity.Term;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.ExamMapper;
import com.aischool.server.mapper.ExamSubjectMapper;
import com.aischool.server.mapper.ScoreMapper;
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.SubjectMapper;
import com.aischool.server.mapper.TeachMapper;
import com.aischool.server.mapper.TeacherProfileMapper;
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
 * 排名纯 Java 内存计算，零自定义 SQL；报告契约不读排名列，重算零契约风险。
 * 批4 方案A 录入窗口：t_exam.entry_open 开=教师可录、可见自己录入的；关=教师全不可见。
 * 教师侧（TEACHER/HEAD_TEACHER 同口径）仅见 created_by=自己 的分数、排名列一律不可见；ADMIN/LEADER 全量。
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
    private final TeacherProfileMapper teacherProfileMapper;
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
            m.put("entryOpen", examOpen(e));
            m.put("subjectCount", subjectCounts.getOrDefault(e.getId(), 0L));
            return m;
        }).toList();
    }

    // ───────────────── 录入上下文 ─────────────────

    /** 某班在某考试下可操作的科目（批4：教师侧只列自己可操作的科目，录入关闭时为空；ADMIN/LEADER 全量） */
    public List<Map<String, Object>> subjectContext(UserPrincipal user, Long examId, Long classId) {
        checkClassVisible(user, classId);
        Exam exam = requireExam(examId);
        if (isTeacherSide(user) && !examOpen(exam)) {
            return List.of(); // 录入窗口已关：教师无可操作科目（前端空态提示）
        }
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
                })
                .filter(m -> !isTeacherSide(user) || (Boolean) m.get("editable"))
                .toList();
    }

    /** 某班某科成绩单（名册 + 分数/排名；缺分为 null）。批4 教师口径：仅见自己录入的分数、排名不可见 */
    public Map<String, Object> listScores(UserPrincipal user, Long examId, Long subjectId, Long classId) {
        checkClassVisible(user, classId);
        ExamSubject es = requireExamSubject(examId, subjectId);
        boolean teacherSide = isTeacherSide(user);
        if (teacherSide) {
            if (!examOpen(requireExam(examId))) {
                throw new BizException(403, "该考试录入已关闭，成绩不可见");
            }
            if (!canEnter(user, classId, subjectId)) {
                throw new BizException(403, "仅可查看自己任教学科的成绩单");
            }
        }
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
            // 教师侧只显示自己录入的分数（他人录入=不可见），排名列一律抹掉（方案A 汇总/排名不可见）
            boolean own = sc != null && (!teacherSide || user.userId().equals(sc.getCreatedBy()));
            m.put("score", own ? sc.getScore() : null);
            m.put("classRank", !teacherSide && sc != null ? sc.getClassRank() : null);
            m.put("gradeRank", !teacherSide && sc != null ? sc.getGradeRank() : null);
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fullScore", es.getFullScore());
        data.put("teacherSide", teacherSide);
        data.put("editable", canEnter(user, classId, subjectId));
        data.put("rows", rows);
        return data;
    }

    /** 成绩导出（权限同查看 listScores）：当前表格 → xlsx。教师侧仅三列（排名列连字样都不出现）；ADMIN/LEADER 五列含排名 */
    public Exported exportScores(UserPrincipal user, Long examId, Long subjectId, Long classId) {
        Map<String, Object> data = listScores(user, examId, subjectId, classId); // 内含数据域校验
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("rows");
        boolean teacherSide = Boolean.TRUE.equals(data.get("teacherSide"));
        List<Object[]> table = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            table.add(teacherSide
                    ? new Object[]{r.get("studentNo"), r.get("name"), r.get("score")}
                    : new Object[]{r.get("studentNo"), r.get("name"), r.get("score"), r.get("classRank"), r.get("gradeRank")});
        }
        Exam exam = examMapper.selectById(examId);
        Subject subject = subjectMapper.selectById(subjectId);
        Clazz clazz = clazzMapper.selectById(classId);
        String name = "成绩_" + (exam != null ? exam.getName() : examId) + "_"
                + (subject != null ? subject.getName() : subjectId) + "_"
                + (clazz != null ? clazz.getName() : classId) + ".xlsx";
        return new Exported(name, excel.export("成绩", teacherSide
                ? new String[]{"学号", "姓名", "成绩"}
                : new String[]{"学号", "姓名", "成绩", "班级排名", "年级排名"}, table));
    }

    // ───────────────── 录入 / 导入 ─────────────────

    /** 批量录入：rows 中 score=null 表示清除该生该科成绩（批4：教师侧录入窗口关闭时 403） */
    public Map<String, Object> entry(UserPrincipal user, Long examId, Long subjectId, Long classId,
                                     List<RowReq> rows) {
        checkEnterable(user, classId, subjectId);
        ExamSubject es = requireExamSubject(examId, subjectId);
        if (isTeacherSide(user) && !examOpen(requireExam(examId))) {
            throw new BizException(403, "该考试录入已关闭");
        }
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

    /** 成绩录入：管理员 / 领导（批4 全校放开，对称批3.5 教师化口径）/ 本班班主任（全学科）/ 该班该科任课教师 / 档案任教学科命中（2026-08-30 校方拍板放宽） */
    private void checkEnterable(UserPrincipal user, Long classId, Long subjectId) {
        if (!canEnter(user, classId, subjectId)) {
            throw new BizException(403, "只有管理员、领导、本班班主任或该学科任课教师可录入成绩");
        }
    }

    private boolean canEnter(UserPrincipal user, Long classId, Long subjectId) {
        if ("ADMIN".equals(user.role()) || "LEADER".equals(user.role())) {
            return true;
        }
        // 班主任：本班全部学科
        Clazz clazz = clazzMapper.selectById(classId);
        if (clazz != null && user.userId().equals(clazz.getHeadTeacherId())) {
            return true;
        }
        // 任课表显式指派
        if (teachMapper.selectCount(new LambdaQueryWrapper<Teach>()
                .eq(Teach::getTeacherId, user.userId())
                .eq(Teach::getClassId, classId)
                .eq(Teach::getSubjectId, subjectId)) > 0) {
            return true;
        }
        // 档案任教学科（班级可见性已由 checkClassVisible 隔离）
        TeacherProfile profile = teacherProfileMapper.selectById(user.userId());
        return profile != null && subjectId.equals(profile.getSubjectId());
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

    // ───────────────── 批4：录入窗口 + 全校汇总 ─────────────────

    /** 教师侧口径（方案A 同口径）：班主任与任课教师都只可见/可录自己录入的 */
    private static boolean isTeacherSide(UserPrincipal user) {
        return "TEACHER".equals(user.role()) || "HEAD_TEACHER".equals(user.role());
    }

    /** 考试录入窗口是否开放（旧数据无值=开） */
    private static boolean examOpen(Exam exam) {
        return exam == null || exam.getEntryOpen() == null || exam.getEntryOpen() == 1;
    }

    /** 开关录入窗口（管理端考试页签；ADMIN/有 ADMIN_ACCESS 的领导） */
    public void setEntryOpen(Long examId, boolean open) {
        Exam exam = requireExam(examId);
        exam.setEntryOpen(open ? 1 : 0);
        examMapper.updateById(exam);
    }

    /**
     * 全校成绩汇总（批4 领导端/管理端）：subjectId 空=总分模式（每生全部科目得分之和），否则单科模式。
     * 年级排名同分同名次；各班统计=参考人数（有分）/平均分/最高分。rows 分页（防几千学生全量下发）。
     */
    public Map<String, Object> scoreSummary(Long examId, Long subjectId, int page, int size) {
        Exam exam = requireExam(examId);
        List<ExamSubject> subjects = examSubjectMapper.selectList(new LambdaQueryWrapper<ExamSubject>()
                .eq(ExamSubject::getExamId, examId).orderByAsc(ExamSubject::getSubjectId));
        boolean bySubject = subjectId != null;
        if (bySubject && subjects.stream().noneMatch(s -> s.getSubjectId().equals(subjectId))) {
            throw new BizException(404, "该考试未设置此科目");
        }
        Map<Long, Student> students = studentMapper.selectList(null).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a));
        Map<Long, Clazz> clazzMap = clazzMapper.selectList(null).stream()
                .collect(Collectors.toMap(Clazz::getId, Function.identity(), (a, b) -> a));
        Map<Long, String> subjectNames = subjectMapper.selectList(null).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName));
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        for (Score s : scoreMapper.selectList(new LambdaQueryWrapper<Score>().eq(Score::getExamId, examId))) {
            if (bySubject) {
                if (s.getSubjectId().equals(subjectId)) {
                    totals.put(s.getStudentId(), s.getScore());
                }
            } else {
                totals.merge(s.getStudentId(), s.getScore(), BigDecimal::add);
            }
        }
        // 全体竞争排名（同分同名次），按分数降序、学号升序稳定排序
        List<Map.Entry<Long, BigDecimal>> sorted = totals.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        int rank = 0;
        BigDecimal prev = null;
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<Long, BigDecimal> e = sorted.get(i);
            if (prev == null || e.getValue().compareTo(prev) != 0) {
                rank = i + 1;
                prev = e.getValue();
            }
            Student st = students.get(e.getKey());
            if (st == null) {
                continue;
            }
            Clazz cz = st.getClassId() == null ? null : clazzMap.get(st.getClassId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("studentId", st.getId());
            m.put("studentNo", st.getStudentNo());
            m.put("name", st.getName());
            m.put("className", cz == null ? "—" : cz.getName());
            m.put("score", e.getValue());
            m.put("gradeRank", rank);
            rows.add(m);
        }
        // 各班统计：参考人数（有分）/平均分/最高分（按排名序稳定取最高）
        Map<Long, List<Map<String, Object>>> byClass = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            Student st = students.get(r.get("studentId"));
            if (st != null && st.getClassId() != null) {
                byClass.computeIfAbsent(st.getClassId(), k -> new ArrayList<>()).add(r);
            }
        }
        List<Map<String, Object>> classStats = byClass.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(en -> {
                    List<BigDecimal> vals = en.getValue().stream().map(r -> (BigDecimal) r.get("score")).toList();
                    Clazz cz = clazzMap.get(en.getKey());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("classId", en.getKey());
                    m.put("className", cz == null ? String.valueOf(en.getKey()) : cz.getName());
                    m.put("count", vals.size());
                    m.put("avg", vals.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(vals.size()), 1, java.math.RoundingMode.HALF_UP));
                    m.put("max", vals.stream().max(Comparator.naturalOrder()).orElse(null));
                    return m;
                }).toList();
        // 分页
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(rows.size(), from + size);
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> examInfo = new LinkedHashMap<>();
        examInfo.put("id", exam.getId());
        examInfo.put("name", exam.getName());
        examInfo.put("termName", termName(exam.getTermId()));
        examInfo.put("examDate", exam.getExamDate());
        examInfo.put("entryOpen", examOpen(exam));
        data.put("exam", examInfo);
        data.put("mode", bySubject ? "subject" : "total");
        data.put("subjectId", subjectId);
        data.put("subjectName", bySubject ? subjectNames.get(subjectId) : null);
        data.put("subjects", subjects.stream().map(s -> Map.of(
                "subjectId", s.getSubjectId(), "name", subjectNames.getOrDefault(s.getSubjectId(), String.valueOf(s.getSubjectId())),
                "fullScore", s.getFullScore())).toList());
        data.put("fullScore", bySubject
                ? subjects.stream().filter(s -> s.getSubjectId().equals(subjectId)).findFirst().map(ExamSubject::getFullScore).orElse(null)
                : subjects.stream().map(ExamSubject::getFullScore).filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        data.put("total", rows.size());
        data.put("classStats", classStats);
        data.put("rows", from >= rows.size() ? List.of() : rows.subList(from, to));
        return data;
    }

    /** 全校汇总导出（权限同查看）：全量年级排名 → xlsx */
    public Exported exportSummary(Long examId, Long subjectId) {
        Map<String, Object> data = scoreSummary(examId, subjectId, 1, Integer.MAX_VALUE);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("rows");
        List<Object[]> table = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            table.add(new Object[]{r.get("studentNo"), r.get("name"), r.get("className"),
                    r.get("score"), r.get("gradeRank")});
        }
        Exam exam = examMapper.selectById(examId);
        String subjectName = (String) data.get("subjectName");
        String name = "成绩汇总_" + (exam != null ? exam.getName() : examId)
                + (subjectName != null ? "_" + subjectName : "_总分") + ".xlsx";
        return new Exported(name, excel.export("成绩汇总",
                new String[]{"学号", "姓名", "班级", subjectName != null ? "成绩" : "总分", "年级名次"}, table));
    }

    private String termName(Long termId) {
        Term t = termMapper.selectById(termId);
        return t == null ? null : t.getName();
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
