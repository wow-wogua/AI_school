package com.aischool.server.controller;

import com.aischool.server.service.asset.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * 素材公共读取（批35 素材库）：免登 GET（SecurityConfig 放行）——
 * web 端「远程 URL 优先 + 内置默认兜底」，未自定义的 key 不出现在 manifest，
 * 前端不会请求本端点。流式 + ETag + 10 分钟缓存（ContentController 高并发样板）。
 */
@RestController
@RequestMapping("/api/asset")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    /** 已自定义素材清单 {key: version}；App 启动拉一次，version 进 URL 防缓存。
     *  清单本身必须新鲜（上传即生效），故 no-cache；图片本体才长缓存。 */
    @GetMapping("/manifest")
    public ResponseEntity<Map<String, String>> manifest() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(assetService.manifest());
    }

    @GetMapping("/{key}")
    public ResponseEntity<InputStreamResource> file(@PathVariable String key) {
        AssetService.AssetStream a = assetService.get(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.contentType()))
                .eTag("\"" + a.version() + "\"")
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(new InputStreamResource(a.in()));
    }
}
