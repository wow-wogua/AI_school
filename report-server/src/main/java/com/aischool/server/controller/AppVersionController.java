package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.AppRelease;
import com.aischool.server.mapper.AppReleaseMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.auth.PermissionService;
import com.aischool.server.service.report.PdfStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * App 在线升级：管理端「版本更新」页签上传 APK（t_app_release + MinIO），
 * 老师端 App 启动比对 versionCode 弹窗提示，点击后下载安装自助升级。
 */
@RestController
@RequiredArgsConstructor
public class AppVersionController {

    private static final String APK_MIME = "application/vnd.android.package-archive";

    private final AppReleaseMapper releaseMapper;
    private final UserMapper userMapper;
    private final PdfStoreService pdfStore;
    private final PermissionService permissionService;

    // ---------- 老师端（需登录） ----------

    /** 最新版本（App 启动/手动检查更新调用；未发布过任何版本时 data=null，App 端静默跳过） */
    @GetMapping("/api/app/latest")
    public ApiResponse<Map<String, Object>> latest() {
        AppRelease r = latestRelease();
        return ApiResponse.ok(r == null ? null : toLatest(r));
    }

    /** 安装包下载（App 原生插件带 Authorization 头拉流；attachment 文件名带版本） */
    @GetMapping("/api/app/{id}/apk")
    public ResponseEntity<byte[]> apk(@PathVariable Long id) throws IOException {
        AppRelease r = releaseMapper.selectById(id);
        if (r == null) {
            throw new BizException(404, "版本不存在");
        }
        byte[] bytes;
        try (InputStream in = pdfStore.download(r.getFileUrl())) {
            bytes = in.readAllBytes();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(APK_MIME));
        headers.setContentLength(bytes.length);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=shishi-growth-v" + r.getVersionName() + ".apk");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    // ---------- 管理端（仅 ADMIN） ----------

    /** 发布新版本：multipart（file=apk、versionName、notes?、force?） */
    @PostMapping("/api/admin/app/release")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                   @RequestParam String versionName,
                                                   @RequestParam(required = false) String notes,
                                                   @RequestParam(required = false, defaultValue = "false") Boolean force,
                                                   @RequestParam Integer versionCode) {
        checkAdmin();
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            throw new BizException(400, "请上传 .apk 安装包");
        }
        if (versionCode == null || versionCode <= 0) {
            throw new BizException(400, "versionCode 必须为正整数（打包脚本会显示）");
        }
        AppRelease exists = latestRelease();
        if (exists != null && versionCode <= exists.getVersionCode()) {
            throw new BizException(400, "versionCode 须大于当前最新版本 " + exists.getVersionCode());
        }
        String objectName = "app/release-" + versionCode + ".apk";
        try {
            pdfStore.upload(objectName, file.getInputStream(), file.getSize(), APK_MIME);
        } catch (IOException e) {
            throw new BizException(500, "安装包存储失败：" + e.getMessage());
        }
        AppRelease r = new AppRelease();
        r.setVersionCode(versionCode);
        r.setVersionName(versionName);
        r.setNotes(notes);
        r.setFileUrl(objectName);
        r.setFileSize(file.getSize());
        r.setForceFlag(Boolean.TRUE.equals(force) ? 1 : 0);
        r.setCreatedBy(AuthUtil.current().userId());
        releaseMapper.insert(r);
        // 旧版本对象随之失去下载入口（列表仍可见记录），顺手清理避免 MinIO 无限累积
        if (exists != null) {
            pdfStore.delete(exists.getFileUrl());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("releaseId", r.getId());
        return ApiResponse.ok(data);
    }

    /** 历史版本列表（versionCode 倒序） */
    @GetMapping("/api/admin/app/release/list")
    public ApiResponse<List<Map<String, Object>>> list() {
        checkAdmin();
        List<AppRelease> rows = releaseMapper.selectList(
                new LambdaQueryWrapper<AppRelease>()
                        .orderByDesc(AppRelease::getVersionCode)
                        .orderByDesc(AppRelease::getId)
                        .last("LIMIT 50"));
        return ApiResponse.ok(rows.stream().map(this::toRow).collect(Collectors.toList()));
    }

    /** 撤回版本（删记录与安装包；若撤的是最新版，App 端自动回退到前一版本） */
    @DeleteMapping("/api/admin/app/release/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        checkAdmin();
        AppRelease r = releaseMapper.selectById(id);
        if (r == null) {
            throw new BizException(404, "版本不存在");
        }
        releaseMapper.deleteById(id);
        pdfStore.delete(r.getFileUrl());
        return ApiResponse.ok();
    }

    // ---------- 内部 ----------

    /** 最新版本=versionCode 最大者（撤回最新后自然回退；versionCode 上传时强制递增，正常只有一条最大） */
    private AppRelease latestRelease() {
        return releaseMapper.selectOne(new LambdaQueryWrapper<AppRelease>()
                .orderByDesc(AppRelease::getVersionCode)
                .orderByDesc(AppRelease::getId)
                .last("LIMIT 1"));
    }

    private Map<String, Object> toLatest(AppRelease r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("versionCode", r.getVersionCode());
        m.put("versionName", r.getVersionName());
        m.put("notes", r.getNotes() == null ? "" : r.getNotes());
        m.put("force", r.getForceFlag() != null && r.getForceFlag() == 1);
        m.put("size", r.getFileSize());
        m.put("url", "/api/app/" + r.getId() + "/apk");
        return m;
    }

    private Map<String, Object> toRow(AppRelease r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("versionCode", r.getVersionCode());
        m.put("versionName", r.getVersionName());
        m.put("notes", r.getNotes() == null ? "" : r.getNotes());
        m.put("size", r.getFileSize());
        m.put("force", r.getForceFlag() != null && r.getForceFlag() == 1);
        m.put("createdBy", r.getCreatedBy());
        String uploader = "";
        if (r.getCreatedBy() != null && userMapper.selectById(r.getCreatedBy()) != null) {
            uploader = String.valueOf(userMapper.selectById(r.getCreatedBy()).getRealName());
        }
        m.put("uploader", uploader);
        m.put("createTime", r.getCreateTime() == null ? "" : r.getCreateTime().toString());
        return m;
    }

    private void checkAdmin() {
        permissionService.checkAdminAccess("只有管理员可操作系统管理");
    }
}
