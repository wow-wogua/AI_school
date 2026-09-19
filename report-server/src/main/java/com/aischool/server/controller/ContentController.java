package com.aischool.server.controller;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.ContentItem;
import com.aischool.server.mapper.ContentItemMapper;
import com.aischool.server.service.report.PdfStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;

/**
 * 内容封面预览（inline，登录即可——家长端列表缩略图/详情大图走这里）。
 * 高并发要点（家长端可达万人级）：流式转发不整包进堆；ETag=objectName（封面替换即变）+
 * Cache-Control 10 分钟——家长端重复浏览命中缓存/304，MinIO 与本服务带宽近似归零。
 */
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ContentController {

    private final ContentItemMapper contentMapper;
    private final PdfStoreService pdfStore;

    @GetMapping("/file/{id}")
    public ResponseEntity<InputStreamResource> file(@PathVariable Long id) {
        ContentItem it = contentMapper.selectById(id);
        if (it == null || it.getCoverUrl() == null) {
            throw new BizException(404, "封面不存在");
        }
        InputStream in = pdfStore.download(it.getCoverUrl());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentTypeOf(it.getCoverUrl())))
                // If-None-Match 命中时 Spring 自动回 304（body 已吐流也不重发）
                .eTag("\"" + it.getCoverUrl() + "\"")
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(new InputStreamResource(in));
    }

    private String contentTypeOf(String objectName) {
        String ext = objectName.substring(objectName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return ext.equals("png") ? "image/png" : "image/jpeg";
    }
}
