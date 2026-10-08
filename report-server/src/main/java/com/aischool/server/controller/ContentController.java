package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.ContentItem;
import com.aischool.server.mapper.ContentItemMapper;
import com.aischool.server.service.report.PdfStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 内容封面预览（inline，登录即可——家长端列表缩略图/详情大图走这里）。
 * 批37 加教师端内容接口（/notices：通知公告 NOTICE / 扬长课程 PARENTING，全校范围，登录即可）。
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

    // ────────────────────────── 通知公告 / 扬长课程（批37 教师端入口） ──────────────────────────

    /** 已发布全校内容（教师/领导 App 内查看；CLASS 范围面向家长走 /api/parent/contents，此处不含） */
    @GetMapping("/notices")
    public ApiResponse<List<Map<String, Object>>> notices(@RequestParam(defaultValue = "NOTICE") String type) {
        if (!"NOTICE".equals(type) && !"PARENTING".equals(type)) {
            throw new BizException(400, "type 须为 NOTICE 或 PARENTING");
        }
        List<ContentItem> rows = contentMapper.selectList(new LambdaQueryWrapper<ContentItem>()
                .eq(ContentItem::getType, type)
                .eq(ContentItem::getStatus, 1)
                .eq(ContentItem::getScope, "ALL")
                .orderByDesc(ContentItem::getPublishTime)
                .orderByDesc(ContentItem::getId)
                .last("LIMIT 50"));
        return ApiResponse.ok(rows.stream().map(it -> noticeRow(it, false)).toList());
    }

    /** 内容详情（全文；仅已发布全校通知公告/扬长课程可达） */
    @GetMapping("/notices/{id}")
    public ApiResponse<Map<String, Object>> noticeDetail(@PathVariable Long id) {
        ContentItem it = contentMapper.selectById(id);
        if (it == null || it.getStatus() == null || it.getStatus() != 1
                || !"ALL".equals(it.getScope())
                || (!"NOTICE".equals(it.getType()) && !"PARENTING".equals(it.getType()))) {
            throw new BizException(404, "内容不存在或未发布");
        }
        return ApiResponse.ok(noticeRow(it, true));
    }

    /** 列表给 80 字摘要（同 ParentController.brief 口径），详情给全文 */
    private Map<String, Object> noticeRow(ContentItem it, boolean full) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", it.getId());
        m.put("type", it.getType());
        m.put("title", it.getTitle());
        m.put("coverUrl", it.getCoverUrl());
        m.put("videoUrl", it.getVideoUrl());
        String content = it.getContent();
        m.put("content", full || content == null || content.length() <= 80
                ? content : content.substring(0, 80) + "…");
        m.put("scope", it.getScope());
        m.put("publishTime", it.getPublishTime() == null ? null : it.getPublishTime().toString());
        return m;
    }

    private String contentTypeOf(String objectName) {
        String ext = objectName.substring(objectName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return ext.equals("png") ? "image/png" : "image/jpeg";
    }
}
