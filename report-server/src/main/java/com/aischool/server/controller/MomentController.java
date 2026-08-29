package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Moment;
import com.aischool.server.mapper.MomentMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.moment.MomentService;
import com.aischool.server.service.report.PdfStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 微光信箱：随手拍记录学生闪光时刻（照片走 MinIO，JWT 内联预览） */
@RestController
@RequestMapping("/api/moment")
@RequiredArgsConstructor
public class MomentController {

    private final MomentService momentService;
    private final MomentMapper momentMapper;
    private final PdfStoreService pdfStore;

    /** 创建（multipart：photo + classId + studentIds + sceneTag + note） */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestParam Long classId,
                                                   @RequestParam(required = false) List<Long> studentIds,
                                                   @RequestParam String sceneTag,
                                                   @RequestParam(required = false) String note,
                                                   @RequestParam("photo") MultipartFile photo) {
        return ApiResponse.ok(momentService.create(AuthUtil.current(), classId, studentIds, sceneTag, note, photo));
    }

    /** 班级最近微光（班级页「本周微光」轮播） */
    @GetMapping("/class")
    public ApiResponse<List<Map<String, Object>>> listByClass(@RequestParam Long classId,
                                                              @RequestParam(defaultValue = "20") Integer limit) {
        return ApiResponse.ok(momentService.listByClass(AuthUtil.current(), classId, limit));
    }

    /** 某学生的微光（学生详情「TA的闪光时刻」） */
    @GetMapping("/student")
    public ApiResponse<List<Map<String, Object>>> listByStudent(@RequestParam Long studentId,
                                                                @RequestParam(defaultValue = "50") Integer limit) {
        return ApiResponse.ok(momentService.listByStudent(AuthUtil.current(), studentId, limit));
    }

    /** 删除（仅记录教师本人或管理员） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        momentService.delete(AuthUtil.current(), id);
        return ApiResponse.ok();
    }

    /** 照片预览（inline，同荣誉证书方式） */
    @GetMapping("/file/{id}")
    public ResponseEntity<byte[]> file(@PathVariable Long id) throws IOException {
        Moment m = momentMapper.selectById(id);
        if (m == null || m.getPhotoUrl() == null) {
            throw new BizException(404, "照片不存在");
        }
        byte[] bytes;
        try (InputStream in = pdfStore.download(m.getPhotoUrl())) {
            bytes = in.readAllBytes();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentTypeOf(m.getPhotoUrl())));
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private String contentTypeOf(String objectName) {
        String ext = objectName.substring(objectName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return ext.equals("png") ? "image/png" : "image/jpeg";
    }
}
