package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Subject;
import com.aischool.server.entity.TeacherProfile;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.SubjectMapper;
import com.aischool.server.mapper.TeacherProfileMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.report.PdfStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** 教师档案：本人查看/维护（照片走 MinIO），管理员可看全员档案并代填代改 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class TeacherProfileController {

    private final TeacherProfileMapper profileMapper;
    private final UserMapper userMapper;
    private final SubjectMapper subjectMapper;
    private final PdfStoreService pdfStore;

    /** 我的档案（账号信息 + 档案字段合并） */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        User u = userMapper.selectById(AuthUtil.current().userId());
        return ApiResponse.ok(view(u, profileMapper.selectById(u.getId()), subjectNames()));
    }

    /** 保存我的档案（upsert；工号全校唯一） */
    @PutMapping("/me")
    public ApiResponse<Map<String, Object>> save(@RequestBody ProfileReq req) {
        return ApiResponse.ok(saveProfile(AuthUtil.current().userId(), req));
    }

    /** 管理员代改教师档案 */
    @PutMapping("/admin/{userId}")
    public ApiResponse<Map<String, Object>> adminSave(@PathVariable Long userId, @RequestBody ProfileReq req) {
        requireAdmin();
        requireUser(userId);
        return ApiResponse.ok(saveProfile(userId, req));
    }

    /** 管理员代传教师照片 */
    @PostMapping("/admin/{userId}/photo")
    public ApiResponse<Map<String, Object>> adminUploadPhoto(@PathVariable Long userId,
            @RequestParam("photo") MultipartFile photo) throws Exception {
        requireAdmin();
        requireUser(userId);
        return uploadPhotoTo(userId, photo);
    }

    private void requireAdmin() {
        if (!"ADMIN".equals(AuthUtil.current().role())) {
            throw new BizException(403, "只有管理员可操作");
        }
    }

    private void requireUser(Long userId) {
        if (userMapper.selectById(userId) == null) {
            throw new BizException(404, "账号不存在");
        }
    }

    /** 档案 upsert（本人/管理员代改共用；工号全校唯一） */
    private Map<String, Object> saveProfile(Long userId, ProfileReq req) {
        if (req.employeeNo != null && !req.employeeNo.isBlank()) {
            TeacherProfile dup = profileMapper.selectOne(new LambdaQueryWrapper<TeacherProfile>()
                    .eq(TeacherProfile::getEmployeeNo, req.employeeNo.trim())
                    .ne(TeacherProfile::getUserId, userId).last("LIMIT 1"));
            if (dup != null) {
                throw new BizException(400, "工号已被 " + userMapper.selectById(dup.getUserId()).getRealName() + " 使用");
            }
        }
        TeacherProfile p = profileMapper.selectById(userId);
        boolean exists = p != null;
        if (p == null) {
            p = new TeacherProfile();
            p.setUserId(userId);
        }
        p.setEmployeeNo(blankToNull(req.employeeNo));
        p.setGender(blankToNull(req.gender));
        p.setSubjectId(req.subjectId);
        p.setTitle(blankToNull(req.title));
        p.setDuty(blankToNull(req.duty));
        p.setTeachingYears(req.teachingYears);
        p.setIntro(blankToNull(req.intro));
        p.setHireDate(req.hireDate);
        if (exists) {
            profileMapper.updateById(p);
        } else {
            profileMapper.insert(p);
        }
        return view(userMapper.selectById(userId), p, subjectNames());
    }

    /** 上传/更换我的照片（jpg/png，≤5MB，存 MinIO teacher/{userId}/{uuid}.{ext}） */
    @PostMapping("/me/photo")
    public ApiResponse<Map<String, Object>> uploadPhoto(@RequestParam("photo") MultipartFile photo) throws Exception {
        return uploadPhotoTo(AuthUtil.current().userId(), photo);
    }

    /** 照片上传共用（本人/管理员代传）：校验→MinIO→回写档案→删旧照 */
    private ApiResponse<Map<String, Object>> uploadPhotoTo(Long userId, MultipartFile photo) throws Exception {
        String ext = extOf(photo.getOriginalFilename());
        if (!ext.equals("png") && !ext.equals("jpg")) {
            throw new BizException(400, "仅支持 jpg/png 图片");
        }
        if (photo.getSize() > 5 * 1024 * 1024) {
            throw new BizException(400, "照片不能超过 5MB");
        }
        byte[] bytes = photo.getBytes();
        String objectName = "teacher/" + userId + "/" + UUID.randomUUID() + "." + ext;
        pdfStore.upload(objectName, new java.io.ByteArrayInputStream(bytes), bytes.length, photo.getContentType());

        TeacherProfile p = profileMapper.selectById(userId);
        String old = p == null ? null : p.getPhotoUrl();
        if (p == null) {
            p = new TeacherProfile();
            p.setUserId(userId);
            p.setPhotoUrl(objectName);
            profileMapper.insert(p);
        } else {
            p.setPhotoUrl(objectName);
            profileMapper.updateById(p);
        }
        if (old != null && !old.isBlank()) {
            pdfStore.delete(old);
        }
        return ApiResponse.ok(Map.of("photoUrl", "/api/profile/photo/" + userId));
    }

    /** 照片预览（inline，JWT 拉流同微光方式） */
    @GetMapping("/photo/{userId}")
    public ResponseEntity<byte[]> photo(@PathVariable Long userId) throws Exception {
        TeacherProfile p = profileMapper.selectById(userId);
        if (p == null || p.getPhotoUrl() == null) {
            throw new BizException(404, "照片不存在");
        }
        byte[] bytes;
        try (InputStream in = pdfStore.download(p.getPhotoUrl())) {
            bytes = in.readAllBytes();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                p.getPhotoUrl().endsWith(".png") ? "image/png" : "image/jpeg"));
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    /** 全员档案（管理员；管理端教师管理扩展列） */
    @GetMapping("/admin/list")
    public ApiResponse<List<Map<String, Object>>> adminList() {
        if (!"ADMIN".equals(AuthUtil.current().role())) {
            throw new BizException(403, "只有管理员可查看全员档案");
        }
        Map<Long, TeacherProfile> byUser = profileMapper.selectList(null).stream()
                .collect(Collectors.toMap(TeacherProfile::getUserId, p -> p));
        Map<Long, String> subjectNames = subjectNames();
        return ApiResponse.ok(userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getRole, "ADMIN", "HEAD_TEACHER", "TEACHER").orderByAsc(User::getId))
                .stream().map(u -> view(u, byUser.get(u.getId()), subjectNames)).toList());
    }

    // ────────────────────────── helpers ──────────────────────────

    private Map<Long, String> subjectNames() {
        return subjectMapper.selectList(null).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName));
    }

    private Map<String, Object> view(User u, TeacherProfile p, Map<Long, String> subjectNames) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getId());
        m.put("realName", u.getRealName());
        m.put("role", u.getRole());
        m.put("phone", u.getPhone());
        if (p == null) {
            m.put("hasProfile", false);
            return m;
        }
        m.put("hasProfile", true);
        m.put("employeeNo", p.getEmployeeNo());
        m.put("gender", p.getGender());
        m.put("subjectId", p.getSubjectId());
        m.put("subjectName", p.getSubjectId() == null ? null : subjectNames.get(p.getSubjectId()));
        m.put("title", p.getTitle());
        m.put("duty", p.getDuty());
        m.put("teachingYears", p.getTeachingYears());
        m.put("intro", p.getIntro());
        m.put("hireDate", p.getHireDate() == null ? null : p.getHireDate().toString());
        m.put("photoUrl", p.getPhotoUrl() == null ? null : "/api/profile/photo/" + u.getId());
        return m;
    }

    private String extOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                .replace("jpeg", "jpg");
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @Data
    public static class ProfileReq {
        private String employeeNo;
        private String gender;
        private Long subjectId;
        private String title;
        private String duty;
        private Integer teachingYears;
        private String intro;
        private LocalDate hireDate;
    }
}
