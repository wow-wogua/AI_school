package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.service.asset.AssetService;
import com.aischool.server.service.auth.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 管理端「素材库」（批35 学校自治）：上传覆盖 / 恢复内置。
 * 上传即生效：App 内素材立即可见；PDF 图位同步进渲染覆盖目录，下一次生成报告即用新图。
 * 换图不需要发新 APK（App 在线升级模式，WebView 加载服务器页面）。
 */
@RestController
@RequestMapping("/api/admin/asset")
@RequiredArgsConstructor
public class AdminAssetController {

    /** jpg/png ≤10MB（与证书/微光照片同限） */
    private static final long MAX_SIZE = 10L * 1024 * 1024;

    private final AssetService assetService;
    private final PermissionService permissionService;

    private void checkAdmin() {
        permissionService.checkAdminAccess("只有管理员可操作系统管理");
    }

    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list() {
        checkAdmin();
        return ApiResponse.ok(assetService.list());
    }

    @PostMapping("/upload")
    public ApiResponse<List<Map<String, Object>>> upload(@RequestParam("key") String key,
                                                         @RequestParam("file") MultipartFile file) {
        checkAdmin();
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请选择图片文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(400, "图片不能超过 10MB");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("png")) {
            throw new BizException(400, "仅支持 jpg / png 图片");
        }
        if (ext.equals("jpeg")) {
            ext = "jpg";
        }
        try {
            assetService.upload(key, file.getInputStream(), file.getSize(), ext);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "读取上传文件失败: " + e.getMessage());
        }
        return ApiResponse.ok(assetService.list());
    }

    /** 恢复内置默认图 */
    @DeleteMapping("/{key}")
    public ApiResponse<List<Map<String, Object>>> delete(@PathVariable String key) {
        checkAdmin();
        assetService.delete(key);
        return ApiResponse.ok(assetService.list());
    }
}
