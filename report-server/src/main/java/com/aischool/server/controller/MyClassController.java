package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.ActivitySignup;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.Comment;
import com.aischool.server.entity.Comprehensive;
import com.aischool.server.entity.Evaluation;
import com.aischool.server.entity.Honor;
import com.aischool.server.entity.InviteCode;
import com.aischool.server.entity.Score;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.Subject;
import com.aischool.server.entity.Teach;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.ActivitySignupMapper;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.CommentMapper;
import com.aischool.server.mapper.ComprehensiveMapper;
import com.aischool.server.mapper.EvaluationMapper;
import com.aischool.server.mapper.HonorMapper;
import com.aischool.server.mapper.InviteCodeMapper;
import com.aischool.server.mapper.ScoreMapper;
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.SubjectMapper;
import com.aischool.server.mapper.TeachMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.auth.DataScopeService;
import com.aischool.server.service.excel.ExcelStudentHelper;
import com.aischool.server.service.report.PdfStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 班主任本班自治包（批28）：名单 Excel 导入/调出/移除 + 本班任课维护 + 交接班一键转移。
 * 班级操作权统一走 checkClassOperable（班主任本班/领导/管理员）；交接班要求发起人=现任班主任。
 */
@RestController
@RequestMapping("/api/my-class")
@RequiredArgsConstructor
public class MyClassController {

    private final ClazzMapper clazzMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final TeachMapper teachMapper;
    private final SubjectMapper subjectMapper;
    private final InviteCodeMapper inviteMapper;
    private final DataScopeService dataScope;
    private final ExcelStudentHelper excelStudent;
    private final PdfStoreService pdfStore;
    private final EvaluationMapper evaluationMapper;
    private final ScoreMapper scoreMapper;
    private final ActivitySignupMapper signupMapper;
    private final ComprehensiveMapper comprehensiveMapper;
    private final CommentMapper commentMapper;
    private final HonorMapper honorMapper;

    private Clazz requireClass(Long classId) {
        if (classId == null) {
            throw new BizException(400, "请携带班级 ID");
        }
        Clazz c = clazzMapper.selectById(classId);
        if (c == null) {
            throw new BizException(404, "班级不存在");
        }
        dataScope.checkClassOperable(AuthUtil.current(), classId);
        return c;
    }

    // ────────────────── 名单：Excel 导入（仅入本班） ──────────────────

    /** 本班学生 Excel 导入：班级列须与本班名一致（或留空），全部行都进本班；逐行校验部分成功 */
    @PostMapping("/students/import")
    public ApiResponse<Map<String, Object>> importStudents(@RequestParam Long classId,
                                                           @RequestParam("file") MultipartFile file) {
        Clazz clazz = requireClass(classId);
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请选择 Excel 文件");
        }
        List<ExcelStudentHelper.StudentRow> rows;
        try (InputStream in = file.getInputStream()) {
            rows = excelStudent.read(in);
        } catch (Exception e) {
            throw new BizException(400, "读取上传文件失败");
        }
        if (rows.isEmpty()) {
            throw new BizException(400, "Excel 里没有数据行（首行为表头，请从第 2 行开始填写）");
        }
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> errors = new java.util.ArrayList<>();
        int inserted = 0;
        for (ExcelStudentHelper.StudentRow r : rows) {
            String reason = null;
            if (r.studentNo().isBlank()) {
                reason = "学号为空";
            } else if (seen.contains(r.studentNo())) {
                reason = "学号在文件内重复";
            } else if (studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                    .eq(Student::getStudentNo, r.studentNo())) > 0) {
                reason = "学号已存在";
            } else if (r.name().isBlank()) {
                reason = "姓名为空";
            } else if (!r.className().isBlank() && !clazz.getName().equals(r.className())) {
                reason = "班主任只能导入本班学生（班级列留空或填 " + clazz.getName() + "）";
            } else if (!r.gender().isBlank() && !"男".equals(r.gender()) && !"女".equals(r.gender())) {
                reason = "性别只能填 男/女: " + r.gender();
            }
            if (reason != null) {
                errors.add(Map.of("row", r.rowNum(), "reason", reason));
                continue;
            }
            seen.add(r.studentNo());
            Student s = new Student();
            s.setStudentNo(r.studentNo());
            s.setName(r.name());
            s.setGender("男".equals(r.gender()) ? "M" : "女".equals(r.gender()) ? "F" : null);
            s.setClassId(classId);
            s.setStatus("在读");
            s.setGuardianName(r.guardianName().isBlank() ? null : r.guardianName());
            s.setGuardianPhone(r.guardianPhone().isBlank() ? null : r.guardianPhone());
            s.setDormBuilding(r.dormBuilding().isBlank() ? null : r.dormBuilding());
            s.setDormRoom(r.dormRoom().isBlank() ? null : r.dormRoom());
            s.setDormBed(r.dormBed().isBlank() ? null : r.dormBed());
            studentMapper.insert(s);
            inserted++;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inserted", inserted);
        data.put("failed", errors.size());
        data.put("errors", errors);
        return ApiResponse.ok(data);
    }

    /** 本班导入模板下载（同管理端模板，班级列填本班名或留空） */
    @GetMapping("/students/import-template")
    public ResponseEntity<byte[]> importTemplate(@RequestParam Long classId) {
        requireClass(classId);
        byte[] bytes = excelStudent.template();
        String filename = URLEncoder.encode("本班学生导入模板.xlsx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(bytes.length)
                .body(bytes);
    }

    // ────────────────── 名单：调出 / 移除 ──────────────────

    /** 调出本班（转出）：学生保留档案，退出日常在读口径 */
    @PutMapping("/student/{id}/transfer-out")
    public ApiResponse<Map<String, Object>> transferOut(@PathVariable Long id) {
        Student s = requireOwnStudent(id);
        studentMapper.update(null, new LambdaUpdateWrapper<Student>()
                .eq(Student::getId, id).set(Student::getStatus, "转出"));
        return ApiResponse.ok(Map.of("updated", 1));
    }

    /** 移除学生（删除）：仅限无任何成长数据的误录学生，护栏同管理端 */
    @DeleteMapping("/student/{id}")
    public ApiResponse<Map<String, Object>> removeStudent(@PathVariable Long id) {
        Student s = requireOwnStudent(id);
        String ref = firstReference(id);
        if (ref != null) {
            throw new BizException(400, "该学生已有成长数据（" + ref + "），不可删除，请改用「调出」");
        }
        if (s.getPhotoUrl() != null && !s.getPhotoUrl().isBlank()) {
            pdfStore.delete(s.getPhotoUrl());
        }
        studentMapper.deleteById(id);
        return ApiResponse.ok(Map.of("deleted", 1));
    }

    // ────────────────── 本班任课维护 ──────────────────

    /** 本班任课列表（教师名+科目名） */
    @GetMapping("/teach/list")
    public ApiResponse<List<Map<String, Object>>> teachList(@RequestParam Long classId) {
        requireClass(classId);
        List<Teach> rows = teachMapper.selectList(new LambdaQueryWrapper<Teach>()
                .eq(Teach::getClassId, classId).orderByAsc(Teach::getId));
        if (rows.isEmpty()) {
            return ApiResponse.ok(List.of());
        }
        Map<Long, String> teacherNames = userMapper.selectBatchIds(rows.stream()
                        .map(Teach::getTeacherId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName));
        Map<Long, String> subjectNames = subjectMapper.selectBatchIds(rows.stream()
                        .map(Teach::getSubjectId).distinct().toList()).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName));
        return ApiResponse.ok(rows.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("teacherId", t.getTeacherId());
            m.put("teacherName", teacherNames.getOrDefault(t.getTeacherId(), "(已删除)"));
            m.put("subjectId", t.getSubjectId());
            m.put("subjectName", subjectNames.getOrDefault(t.getSubjectId(), "(科目已删除)"));
            return m;
        }).toList());
    }

    @Data
    public static class TeachReq {
        private Long classId;
        private Long teacherId;
        private Long subjectId;
    }

    /** 添加本班任课（同班同科同人不可重复） */
    @PostMapping("/teach")
    public ApiResponse<Map<String, Object>> addTeach(@RequestBody TeachReq req) {
        requireClass(req.getClassId());
        if (req.getTeacherId() == null || req.getSubjectId() == null) {
            throw new BizException(400, "请选择教师与科目");
        }
        User t = userMapper.selectById(req.getTeacherId());
        if (t == null || t.getStatus() == null || t.getStatus() != 1
                || !"TEACHER".equals(t.getRole()) && !"HEAD_TEACHER".equals(t.getRole()) && !"LEADER".equals(t.getRole())) {
            throw new BizException(400, "被选教师不存在或不可任课（仅在职教师/班主任/领导）");
        }
        if (subjectMapper.selectById(req.getSubjectId()) == null) {
            throw new BizException(404, "科目不存在");
        }
        if (teachMapper.selectCount(new LambdaQueryWrapper<Teach>()
                .eq(Teach::getClassId, req.getClassId())
                .eq(Teach::getTeacherId, req.getTeacherId())
                .eq(Teach::getSubjectId, req.getSubjectId())) > 0) {
            throw new BizException(400, "该教师已是本班此科目任课");
        }
        Teach t2 = new Teach();
        t2.setClassId(req.getClassId());
        t2.setTeacherId(req.getTeacherId());
        t2.setSubjectId(req.getSubjectId());
        teachMapper.insert(t2);
        return ApiResponse.ok(Map.of("teachId", t2.getId()));
    }

    /** 删除本班任课 */
    @DeleteMapping("/teach/{id}")
    public ApiResponse<Void> removeTeach(@PathVariable Long id, @RequestParam Long classId) {
        requireClass(classId);
        Teach t = teachMapper.selectById(id);
        if (t == null || !classId.equals(t.getClassId())) {
            throw new BizException(404, "任课记录不存在");
        }
        teachMapper.deleteById(id);
        return ApiResponse.ok();
    }

    // ────────────────── 交接班 ──────────────────

    /** 可选教师下拉（在职教师侧角色，q=姓名过滤）——导入任课/交接班选择器共用 */
    @GetMapping("/teachers")
    public ApiResponse<List<Map<String, Object>>> teachers(@RequestParam(required = false) String q) {
        String kw = q == null || q.isBlank() ? null : q.trim();
        List<User> rows = userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getRole, "TEACHER", "HEAD_TEACHER", "LEADER")
                .eq(User::getStatus, 1)
                .like(kw != null, User::getRealName, kw)
                .orderByAsc(User::getId)
                .last("LIMIT 100"));
        return ApiResponse.ok(rows.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            m.put("role", u.getRole());
            return m;
        }).toList());
    }

    /** 交接班一键转移：发起人须为本班现任班主任；新班主任就位、发起人卸任降级、旧邀请码全作废 */
    @PutMapping("/handover")
    public ApiResponse<Map<String, Object>> handover(@RequestBody Map<String, Long> req) {
        Long classId = req.get("classId");
        Long newHeadId = req.get("newHeadTeacherId");
        if (newHeadId == null) {
            throw new BizException(400, "请选择新班主任");
        }
        Clazz clazz = clazzMapper.selectById(classId);
        if (clazz == null) {
            throw new BizException(404, "班级不存在");
        }
        var me = AuthUtil.current();
        if (!"HEAD_TEACHER".equals(me.role()) || !me.userId().equals(clazz.getHeadTeacherId())) {
            throw new BizException(403, "只有本班现任班主任可发起交接班");
        }
        if (newHeadId.equals(me.userId())) {
            throw new BizException(400, "新班主任不能是本人");
        }
        User next = userMapper.selectById(newHeadId);
        if (next == null || next.getStatus() == null || next.getStatus() != 1
                || !"TEACHER".equals(next.getRole()) && !"HEAD_TEACHER".equals(next.getRole())
                && !"LEADER".equals(next.getRole())) {
            throw new BizException(400, "新班主任须为在职教师/班主任/领导");
        }
        // 1) 新班主任就位（TEACHER → HEAD_TEACHER）
        clazzMapper.update(null, new LambdaUpdateWrapper<Clazz>()
                .eq(Clazz::getId, classId).set(Clazz::getHeadTeacherId, newHeadId));
        if ("TEACHER".equals(next.getRole())) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, newHeadId).set(User::getRole, "HEAD_TEACHER"));
        }
        // 2) 发起人卸任：不再担任任何班主任 → 降回 TEACHER（教师侧功能不受影响，仅失去本班管理权）
        boolean stillHead = clazzMapper.selectCount(new LambdaQueryWrapper<Clazz>()
                .eq(Clazz::getHeadTeacherId, me.userId())) > 0;
        if (!stillHead) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, me.userId()).set(User::getRole, "TEACHER"));
        }
        // 3) 本班旧邀请码全作废（换班主任后家长绑定走新码，防旧码扩散）
        List<Long> studentIds = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                        .eq(Student::getClassId, classId)
                        .select(Student::getId)).stream().map(Student::getId).toList();
        int voided = 0;
        if (!studentIds.isEmpty()) {
            voided = inviteMapper.update(null, new LambdaUpdateWrapper<InviteCode>()
                    .in(InviteCode::getStudentId, studentIds)
                    .eq(InviteCode::getStatus, InviteCode.UNUSED)
                    .set(InviteCode::getStatus, InviteCode.VOID));
        }
        return ApiResponse.ok(Map.of(
                "newHeadTeacherName", next.getRealName(),
                "demoted", !stillHead,
                "inviteVoided", voided,
                "time", LocalDateTime.now().toString()));
    }

    // ────────────────── 内部护栏 ──────────────────

    private Student requireOwnStudent(Long studentId) {
        Student s = studentMapper.selectById(studentId);
        if (s == null) {
            throw new BizException(404, "学生不存在");
        }
        dataScope.checkClassOperable(AuthUtil.current(), s.getClassId());
        return s;
    }

    /** 学生删除守卫（同管理端）：任一成长数据表有记录即拒删 */
    private String firstReference(Long studentId) {
        if (evaluationMapper.selectCount(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getStudentId, studentId)) > 0) {
            return "评价";
        }
        if (scoreMapper.selectCount(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId)) > 0) {
            return "成绩";
        }
        if (signupMapper.selectCount(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getStudentId, studentId)) > 0) {
            return "活动";
        }
        if (comprehensiveMapper.selectCount(new LambdaQueryWrapper<Comprehensive>()
                .eq(Comprehensive::getStudentId, studentId)) > 0) {
            return "综评";
        }
        if (commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStudentId, studentId)) > 0) {
            return "寄语";
        }
        if (honorMapper.selectCount(new LambdaQueryWrapper<Honor>()
                .eq(Honor::getStudentId, studentId)) > 0) {
            return "荣誉";
        }
        return null;
    }
}
