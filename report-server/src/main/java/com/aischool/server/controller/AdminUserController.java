package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.Subject;
import com.aischool.server.entity.Teach;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.SubjectMapper;
import com.aischool.server.mapper.TeachMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.AuthUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 管理端：教师账号（增删改/重置密码/停用）与任课关系（全部仅管理员） */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserMapper userMapper;
    private final TeachMapper teachMapper;
    private final ClazzMapper clazzMapper;
    private final SubjectMapper subjectMapper;
    private final PasswordEncoder passwordEncoder;

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
