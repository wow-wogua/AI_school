package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.Subject;
import com.aischool.server.entity.Teach;
import com.aischool.server.entity.TeacherProfile;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.SubjectMapper;
import com.aischool.server.mapper.TeachMapper;
import com.aischool.server.mapper.TeacherProfileMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.excel.ExcelTeacherHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 管理端：教师账号（增删改/重置密码/停用/批量导入）与任课关系（全部仅管理员） */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserMapper userMapper;
    private final TeachMapper teachMapper;
    private final ClazzMapper clazzMapper;
    private final SubjectMapper subjectMapper;
    private final TeacherProfileMapper teacherProfileMapper;
    private final ExcelTeacherHelper excelTeacher;
    private final PasswordEncoder passwordEncoder;

    /** 批量导入的统一初始密码（导入后教师可登录 App 自行修改） */
    public static final String INITIAL_PASSWORD = "Shishi@2026";

    private static final List<String> ROLES = List.of("ADMIN", "HEAD_TEACHER", "TEACHER");

    private void checkAdmin() {
        if (!"ADMIN".equals(AuthUtil.current().role())) {
            throw new BizException(403, "只有管理员可操作系统管理");
        }
    }

    @Data
    public static class UserReq {
        @NotBlank(message = "username 不能为空")
        private String username;
        @NotBlank(message = "password 不能为空")
        private String password;
        @NotBlank(message = "realName 不能为空")
        private String realName;
        @NotBlank(message = "role 不能为空")
        private String role;
        private String phone;
    }

    @Data
    public static class UserEditReq {
        private String realName;
        private String role;
        private String phone;
    }

    @Data
    public static class PasswordReq {
        @NotBlank(message = "password 不能为空")
        private String password;
    }

    @Data
    public static class StatusReq {
        @NotNull(message = "status 不能为空")
        private Integer status;
    }

    @Data
    public static class TeachReq {
        @NotNull(message = "teacherId 不能为空")
        private Long teacherId;
        @NotNull(message = "classId 不能为空")
        private Long classId;
        @NotNull(message = "subjectId 不能为空")
        private Long subjectId;
    }

    /** 教师账号列表（分页，含角色/关键词过滤；不回传密码） */
    @GetMapping("/user/list")
    public ApiResponse<Map<String, Object>> userList(@RequestParam(required = false) String role,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "20") long size) {
        checkAdmin();
        var p = userMapper.selectPage(Page.of(page, Math.min(size, 100)), new LambdaQueryWrapper<User>()
                .eq(role != null && !role.isBlank(), User::getRole, role)
                .and(keyword != null && !keyword.isBlank(),
                        q -> q.like(User::getUsername, keyword).or().like(User::getRealName, keyword))
                .orderByAsc(User::getId));
        List<Map<String, Object>> records = p.getRecords().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            m.put("role", u.getRole());
            m.put("phone", u.getPhone());
            m.put("status", u.getStatus());
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", p.getTotal());
        data.put("records", records);
        return ApiResponse.ok(data);
    }

    @PostMapping("/user")
    public ApiResponse<Map<String, Object>> createUser(@Validated @RequestBody UserReq req) {
        checkAdmin();
        if (!ROLES.contains(req.getRole())) {
            throw new BizException(400, "role 必须是 ADMIN/HEAD_TEACHER/TEACHER");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername())) > 0) {
            throw new BizException(400, "用户名已存在");
        }
        User u = new User();
        u.setUsername(req.getUsername());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setRealName(req.getRealName());
        u.setRole(req.getRole());
        u.setPhone(req.getPhone());
        u.setStatus(1);
        userMapper.insert(u);
        return ApiResponse.ok(Map.of("userId", u.getId()));
    }

    /** 教师批量导入（管理员，multipart）：账号+档案+班主任带班一次建；逐行校验部分成功；统一初始密码（教师登录 App 自行修改） */
    @PostMapping("/teacher/import")
    public ApiResponse<Map<String, Object>> importTeachers(@RequestParam("file") MultipartFile file) {
        checkAdmin();
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请选择 Excel 文件");
        }
        List<ExcelTeacherHelper.TeacherRow> rows;
        try (InputStream in = file.getInputStream()) {
            rows = excelTeacher.read(in);
        } catch (IOException e) {
            throw new BizException(400, "读取上传文件失败");
        }
        if (rows.isEmpty()) {
            throw new BizException(400, "Excel 里没有数据行（首行为表头，请从第 2 行开始填写）");
        }
        Map<String, Long> subjectIds = subjectMapper.selectList(null).stream()
                .collect(Collectors.toMap(Subject::getName, Subject::getId, (a, b) -> a));
        Map<String, Long> classIds = clazzMapper.selectList(null).stream()
                .collect(Collectors.toMap(Clazz::getName, Clazz::getId, (a, b) -> a));
        Map<String, String> roleMap = Map.of("管理员", "ADMIN", "班主任", "HEAD_TEACHER", "教师", "TEACHER");
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> errors = new java.util.ArrayList<>();
        int inserted = 0;
        for (ExcelTeacherHelper.TeacherRow r : rows) {
            Integer years = r.teachingYears().isBlank() ? null : parseIntOrNull(r.teachingYears());
            LocalDate hireDate = r.hireDate().isBlank() ? null : parseDateOrNull(r.hireDate());
            boolean hasHeadClass = !r.headClassName().isBlank();
            Clazz headClazz = hasHeadClass && classIds.containsKey(r.headClassName())
                    ? clazzMapper.selectById(classIds.get(r.headClassName())) : null;
            String reason = null;
            if (r.username().isBlank()) {
                reason = "账号为空";
            } else if (seen.contains(r.username())) {
                reason = "账号在文件内重复";
            } else if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, r.username())) > 0) {
                reason = "账号已存在";
            } else if (r.realName().isBlank()) {
                reason = "姓名为空";
            } else if (!roleMap.containsKey(r.role())) {
                reason = "角色只能填 管理员/班主任/教师: " + (r.role().isBlank() ? "(空)" : r.role());
            } else if (!r.gender().isBlank() && !"男".equals(r.gender()) && !"女".equals(r.gender())) {
                reason = "性别只能填 男/女: " + r.gender();
            } else if (!r.subjectName().isBlank() && !subjectIds.containsKey(r.subjectName())) {
                reason = "任教学科不存在: " + r.subjectName();
            } else if (!r.employeeNo().isBlank() && teacherProfileMapper.selectCount(
                    new LambdaQueryWrapper<TeacherProfile>()
                            .eq(TeacherProfile::getEmployeeNo, r.employeeNo())) > 0) {
                reason = "工号已存在: " + r.employeeNo();
            } else if (years == null && !r.teachingYears().isBlank()) {
                reason = "教龄须为数字: " + r.teachingYears();
            } else if (hireDate == null && !r.hireDate().isBlank()) {
                reason = "入职年月格式须为 2026-08-30: " + r.hireDate();
            } else if (hasHeadClass && !"班主任".equals(r.role())) {
                reason = "只有班主任才能带班";
            } else if (hasHeadClass && headClazz == null) {
                reason = "班主任所带班级不存在: " + r.headClassName();
            } else if (headClazz != null && headClazz.getHeadTeacherId() != null) {
                reason = "该班已有班主任（请先在班级管理中调整）";
            }
            if (reason != null) {
                errors.add(Map.of("row", r.rowNum(), "reason", reason));
                continue;
            }
            seen.add(r.username());
            User u = new User();
            u.setUsername(r.username());
            u.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
            u.setRealName(r.realName());
            u.setRole(roleMap.get(r.role()));
            u.setPhone(r.phone().isBlank() ? null : r.phone());
            u.setStatus(1);
            userMapper.insert(u);
            boolean anyProfileField = !r.employeeNo().isBlank() || !r.gender().isBlank()
                    || !r.subjectName().isBlank() || !r.title().isBlank() || !r.duty().isBlank()
                    || years != null || hireDate != null || !r.intro().isBlank();
            if (anyProfileField) {
                TeacherProfile p = new TeacherProfile();
                p.setUserId(u.getId());
                p.setEmployeeNo(blankToNull(r.employeeNo()));
                p.setGender(blankToNull(r.gender()));
                p.setSubjectId(r.subjectName().isBlank() ? null : subjectIds.get(r.subjectName()));
                p.setTitle(blankToNull(r.title()));
                p.setDuty(blankToNull(r.duty()));
                p.setTeachingYears(years);
                p.setHireDate(hireDate);
                p.setIntro(blankToNull(r.intro()));
                teacherProfileMapper.insert(p);
            }
            if (headClazz != null) {
                headClazz.setHeadTeacherId(u.getId());
                clazzMapper.updateById(headClazz);
            }
            inserted++;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inserted", inserted);
        data.put("failed", errors.size());
        data.put("errors", errors);
        data.put("initialPassword", INITIAL_PASSWORD);
        return ApiResponse.ok(data);
    }

    /** 教师导入模板下载（仅表头，.xlsx） */
    @GetMapping("/teacher/import-template")
    public ResponseEntity<byte[]> teacherImportTemplate() {
        checkAdmin();
        byte[] bytes = excelTeacher.template();
        String filename = URLEncoder.encode("教师导入模板.xlsx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(bytes.length)
                .body(bytes);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDateOrNull(String s) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @PutMapping("/user/{id}")
    public ApiResponse<Void> updateUser(@PathVariable Long id, @RequestBody UserEditReq req) {
        checkAdmin();
        User u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(404, "账号不存在");
        }
        if (AuthUtil.current().userId().equals(id)
                && req.getRole() != null && !req.getRole().equals(u.getRole())) {
            throw new BizException(400, "不能修改自己的角色");
        }
        if (req.getRole() != null && !ROLES.contains(req.getRole())) {
            throw new BizException(400, "role 必须是 ADMIN/HEAD_TEACHER/TEACHER");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getRealName, req.getRealName() != null ? req.getRealName() : u.getRealName())
                .set(User::getRole, req.getRole() != null ? req.getRole() : u.getRole())
                .set(User::getPhone, req.getPhone()));
        return ApiResponse.ok();
    }

    /** 重置密码 */
    @PutMapping("/user/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @Validated @RequestBody PasswordReq req) {
        checkAdmin();
        if (userMapper.selectById(id) == null) {
            throw new BizException(404, "账号不存在");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getPasswordHash, passwordEncoder.encode(req.getPassword())));
        return ApiResponse.ok();
    }

    /** 停用/启用（不可停用自己） */
    @PutMapping("/user/{id}/status")
    public ApiResponse<Void> updateUserStatus(@PathVariable Long id, @Validated @RequestBody StatusReq req) {
        checkAdmin();
        if (req.getStatus() != 0 && req.getStatus() != 1) {
            throw new BizException(400, "status 必须是 0/1");
        }
        if (AuthUtil.current().userId().equals(id) && req.getStatus() == 0) {
            throw new BizException(400, "不能停用自己");
        }
        if (userMapper.selectById(id) == null) {
            throw new BizException(404, "账号不存在");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id).set(User::getStatus, req.getStatus()));
        return ApiResponse.ok();
    }

    @DeleteMapping("/user/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        checkAdmin();
        if (AuthUtil.current().userId().equals(id)) {
            throw new BizException(400, "不能删除自己");
        }
        if (userMapper.selectById(id) == null) {
            throw new BizException(404, "账号不存在");
        }
        if (teachMapper.selectCount(new LambdaQueryWrapper<Teach>().eq(Teach::getTeacherId, id)) > 0) {
            throw new BizException(400, "该教师仍有任课关系，请先删除任课记录");
        }
        if (clazzMapper.selectCount(new LambdaQueryWrapper<Clazz>().eq(Clazz::getHeadTeacherId, id)) > 0) {
            throw new BizException(400, "该教师仍是某班班主任，请先调整班级");
        }
        userMapper.deleteById(id);
        return ApiResponse.ok();
    }

    /** 任课关系列表（teacherId 可选，附教师/班级/学科名） */
    @GetMapping("/teach/list")
    public ApiResponse<List<Map<String, Object>>> teachList(@RequestParam(required = false) Long teacherId) {
        checkAdmin();
        List<Teach> rows = teachMapper.selectList(new LambdaQueryWrapper<Teach>()
                .eq(teacherId != null, Teach::getTeacherId, teacherId)
                .orderByAsc(Teach::getId));
        Map<Long, String> userNames = names(rows.stream().map(Teach::getTeacherId).toList(),
                ids -> userMapper.selectBatchIds(ids), User::getId, User::getRealName);
        Map<Long, String> classNames = names(rows.stream().map(Teach::getClassId).toList(),
                ids -> clazzMapper.selectBatchIds(ids), Clazz::getId, Clazz::getName);
        Map<Long, String> subjectNames = names(rows.stream().map(Teach::getSubjectId).toList(),
                ids -> subjectMapper.selectBatchIds(ids), Subject::getId, Subject::getName);
        List<Map<String, Object>> out = rows.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("teacherId", t.getTeacherId());
            m.put("teacherName", userNames.get(t.getTeacherId()));
            m.put("classId", t.getClassId());
            m.put("className", classNames.get(t.getClassId()));
            m.put("subjectId", t.getSubjectId());
            m.put("subjectName", subjectNames.get(t.getSubjectId()));
            return m;
        }).toList();
        return ApiResponse.ok(out);
    }

    private <T> Map<Long, String> names(List<Long> ids, Function<List<Long>, List<T>> loader,
                                        Function<T, Long> key, Function<T, String> value) {
        List<Long> distinct = ids.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return loader.apply(distinct).stream().collect(Collectors.toMap(key, value, (a, b) -> a));
    }

    @PostMapping("/teach")
    public ApiResponse<Map<String, Object>> createTeach(@Validated @RequestBody TeachReq req) {
        checkAdmin();
        if (teachMapper.selectCount(new LambdaQueryWrapper<Teach>()
                .eq(Teach::getTeacherId, req.getTeacherId())
                .eq(Teach::getClassId, req.getClassId())
                .eq(Teach::getSubjectId, req.getSubjectId())) > 0) {
            throw new BizException(400, "该任课关系已存在");
        }
        if (userMapper.selectById(req.getTeacherId()) == null) {
            throw new BizException(404, "教师不存在");
        }
        if (clazzMapper.selectById(req.getClassId()) == null) {
            throw new BizException(404, "班级不存在");
        }
        if (subjectMapper.selectById(req.getSubjectId()) == null) {
            throw new BizException(404, "学科不存在");
        }
        Teach t = new Teach();
        t.setTeacherId(req.getTeacherId());
        t.setClassId(req.getClassId());
        t.setSubjectId(req.getSubjectId());
        teachMapper.insert(t);
        return ApiResponse.ok(Map.of("teachId", t.getId()));
    }

    @DeleteMapping("/teach/{id}")
    public ApiResponse<Void> deleteTeach(@PathVariable Long id) {
        checkAdmin();
        if (teachMapper.selectById(id) == null) {
            throw new BizException(404, "任课关系不存在");
        }
        teachMapper.deleteById(id);
        return ApiResponse.ok();
    }
}
