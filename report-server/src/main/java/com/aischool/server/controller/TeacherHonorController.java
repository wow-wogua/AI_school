package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.TeacherHonor;
import com.aischool.server.entity.TeacherProfile;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.TeacherHonorMapper;
import com.aischool.server.mapper.TeacherProfileMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.report.PdfStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 教师成就：证书/荣誉照片（MinIO 存储），全校登录可见，本人上传/删除 */
@RestController
@RequestMapping("/api/teacher-honor")
@RequiredArgsConstructor
public class TeacherHonorController {

    private final TeacherHonorMapper honorMapper;
    private final UserMapper userMapper;
    private final TeacherProfileMapper profileMapper;
    private final PdfStoreService pdfStore;

    /** 上传一条成就（multipart：photo 必填 + name 必填 + level/issuer/honorDate 选填；教师取自 JWT） */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestParam String name,
                                                   @RequestParam(required = false) String level,
                                                   @RequestParam(required = false) String issuer,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate honorDate,
                                                   @RequestParam("photo") MultipartFile photo) throws IOException {
        Long userId = AuthUtil.current().userId();
        if (name == null || name.isBlank()) {
            throw new BizException(400, "请填写奖项名称");
        }
        String ext = extOf(photo.getOriginalFilename());
        if (!ext.equals("png") && !ext.equals("jpg")) {
            throw new BizException(400, "仅支持 jpg/png 图片");
        }
        if (photo.getSize() > 10 * 1024 * 1024) {
            throw new BizException(400, "证书照片不能超过 10MB");
        }
        byte[] bytes = photo.getBytes();
        String objectName = "teacher-honor/" + userId + "/" + UUID.randomUUID() + "." + ext;
        pdfStore.upload(objectName, new ByteArrayInputStream(bytes), bytes.length, photo.getContentType());

        TeacherHonor h = new TeacherHonor();
        h.setTeacherId(userId);
        h.setName(name.trim());
        h.setLevel(blankToNull(level));
        h.setIssuer(blankToNull(issuer));
        h.setHonorDate(honorDate);
        h.setFileUrl(objectName);
        honorMapper.insert(h);
        return ApiResponse.ok(Map.of("id", h.getId()));
    }

    /** 全校成就墙（登录即可；teacherId 可选过滤） */
    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) Long teacherId,
                                                       @RequestParam(defaultValue = "100") Integer limit) {
        List<TeacherHonor> rows = honorMapper.selectList(new LambdaQueryWrapper<TeacherHonor>()
                .eq(teacherId != null, TeacherHonor::getTeacherId, teacherId)
                .orderByDesc(TeacherHonor::getCreateTime)
                .last("LIMIT " + Math.min(limit, 200)));
        return ApiResponse.ok(assemble(rows));
    }

    /** 有成就的教师（展示页筛选条：姓名/照片/条数） */
    @GetMapping("/teachers")
    public ApiResponse<List<Map<String, Object>>> teachers() {
        Map<Long, Long> counts = honorMapper.selectList(new LambdaQueryWrapper<TeacherHonor>()
                        .orderByDesc(TeacherHonor::getCreateTime)).stream()
                .collect(Collectors.groupingBy(TeacherHonor::getTeacherId, Collectors.counting()));
        if (counts.isEmpty()) {
            return ApiResponse.ok(List.of());
        }
        Set<Long> ids = counts.keySet();
        Map<Long, String> names = userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName));
        Map<Long, TeacherProfile> profiles = profileMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(TeacherProfile::getUserId, p -> p));
        return ApiResponse.ok(counts.keySet().stream()
                .sorted(Comparator.comparing((Long id) -> counts.get(id)).reversed()
                        .thenComparing(id -> id))
                .map(id -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("teacherId", id);
                    m.put("realName", names.getOrDefault(id, "未知教师"));
                    m.put("photoUrl", profiles.containsKey(id) && profiles.get(id).getPhotoUrl() != null
                            ? "/api/profile/photo/" + id : null);
                    m.put("count", counts.get(id));
                    return m;
                }).toList());
    }

    /** 我的成就（上传页「我的成就」管理区） */
    @GetMapping("/my")
    public ApiResponse<List<Map<String, Object>>> my() {
        List<TeacherHonor> rows = honorMapper.selectList(new LambdaQueryWrapper<TeacherHonor>()
                .eq(TeacherHonor::getTeacherId, AuthUtil.current().userId())
                .orderByDesc(TeacherHonor::getCreateTime));
        return ApiResponse.ok(assemble(rows));
    }

    /** 删除（本人或管理员；连带删 MinIO 对象） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        TeacherHonor h = honorMapper.selectById(id);
        if (h == null) {
            throw new BizException(404, "成就不存在");
        }
        if (!h.getTeacherId().equals(AuthUtil.current().userId())
                && !"ADMIN".equals(AuthUtil.current().role())) {
            throw new BizException(403, "只能删除自己的成就");
        }
        honorMapper.deleteById(id);
        if (h.getFileUrl() != null && !h.getFileUrl().isBlank()) {
            pdfStore.delete(h.getFileUrl());
        }
        return ApiResponse.ok();
    }

    /** 证书照片预览（inline，JWT 拉流同微光方式） */
    @GetMapping("/file/{id}")
    public ResponseEntity<byte[]> file(@PathVariable Long id) throws IOException {
        TeacherHonor h = honorMapper.selectById(id);
        if (h == null || h.getFileUrl() == null) {
            throw new BizException(404, "证书不存在");
        }
        byte[] bytes;
        try (InputStream in = pdfStore.download(h.getFileUrl())) {
            bytes = in.readAllBytes();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                h.getFileUrl().endsWith(".png") ? "image/png" : "image/jpeg"));
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    // ────────────────────────── helpers ──────────────────────────

    /** 视图组装：补教师姓名，photoUrl 输出为 JWT 保护的虚拟路径 */
    private List<Map<String, Object>> assemble(List<TeacherHonor> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = userMapper.selectBatchIds(
                        rows.stream().map(TeacherHonor::getTeacherId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName));
        return rows.stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("teacherId", h.getTeacherId());
            m.put("teacherName", names.get(h.getTeacherId()));
            m.put("name", h.getName());
            m.put("level", h.getLevel());
            m.put("issuer", h.getIssuer());
            m.put("honorDate", h.getHonorDate() == null ? null : h.getHonorDate().toString());
            m.put("photoUrl", "/api/teacher-honor/file/" + h.getId());
            m.put("createTime", h.getCreateTime() == null ? null : h.getCreateTime().toString());
            return m;
        }).toList();
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
}
