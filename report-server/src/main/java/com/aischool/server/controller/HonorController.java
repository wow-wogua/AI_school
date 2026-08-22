package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Honor;
import com.aischool.server.entity.Student;
import com.aischool.server.mapper.HonorMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.auth.DataScopeService;
import com.aischool.server.service.honor.HonorService;
import com.aischool.server.service.report.PdfStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 荣誉与证书：上传（AI 识别/手动）→ 教师确认生效；证书原件可在线预览 */
@RestController
@RequestMapping("/api/honor")
@RequiredArgsConstructor
public class HonorController {

    private final HonorMapper honorMapper;
    private final HonorService honorService;
    private final DataScopeService dataScope;
    private final PdfStoreService pdfStore;

    @Data
    public static class SaveReq {
        private String name;
        private String level;
        private String issuer;
        private LocalDate honorDate;
    }

    @Data
    public static class ConfirmReq {
        /** 确认时可选入账能量币 */
        private BigDecimal coin;
    }

    /** 上传证书（multipart：file + studentId） */
    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(@RequestParam Long studentId,
                                                   @RequestParam("file") MultipartFile file) {
        checkWritable(studentId);
        return ApiResponse.ok(honorService.upload(studentId, file));
    }

    /** 某学生的荣誉列表（create_time 倒序，含待确认） */
    @GetMapping("/list")
    public ApiResponse<List<Honor>> list(@RequestParam Long studentId) {
        dataScope.checkStudentAccess(AuthUtil.current(), studentId);
        return ApiResponse.ok(honorMapper.selectList(new LambdaQueryWrapper<Honor>()
                .eq(Honor::getStudentId, studentId)
                .orderByDesc(Honor::getCreateTime)
                .orderByDesc(Honor::getId)));
    }

    /** 编辑待确认荣誉字段 */
    @PutMapping("/{id}")
    public ApiResponse<Void> save(@PathVariable Long id, @RequestBody SaveReq req) {
        Honor h = requireHonor(id);
        checkWritable(h.getStudentId());
        honorService.save(id, req.getName(), req.getLevel(), req.getIssuer(), req.getHonorDate());
        return ApiResponse.ok();
    }

    /** 确认生效（可选能量币入账） */
    @PutMapping("/{id}/confirm")
    public ApiResponse<Map<String, Object>> confirm(@PathVariable Long id,
                                                    @Validated @RequestBody ConfirmReq req) {
        Honor h = requireHonor(id);
        checkWritable(h.getStudentId());
        return ApiResponse.ok(honorService.confirm(id, req.getCoin()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Honor h = requireHonor(id);
        checkWritable(h.getStudentId());
        honorService.delete(id);
        return ApiResponse.ok();
    }

    /** 证书原件预览（inline；图片直显，PDF 同报告预览方式） */
    @GetMapping("/file/{id}")
    public ResponseEntity<byte[]> file(@PathVariable Long id) throws IOException {
        var user = AuthUtil.current();
        Honor h = requireHonor(id);
        dataScope.checkStudentAccess(user, h.getStudentId());
        if (h.getFileUrl() == null) {
            throw new BizException(404, "证书文件不存在");
        }
        byte[] bytes;
        try (InputStream in = pdfStore.download(h.getFileUrl())) {
            bytes = in.readAllBytes();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentTypeOf(h.getFileUrl())));
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    // ───────────────── 权限 ─────────────────

    /** 写（上传/编辑/确认/删除）：管理员或该班班主任 */
    private void checkWritable(Long studentId) {
        var user = AuthUtil.current();
        Student student = dataScope.checkStudentAccess(user, studentId);
        if (!"ADMIN".equals(user.role())) {
            dataScope.checkClassOperable(user, student.getClassId());
        }
    }

    private Honor requireHonor(Long id) {
        Honor h = honorMapper.selectById(id);
        if (h == null) {
            throw new BizException(404, "荣誉记录不存在");
        }
        return h;
    }

    private String contentTypeOf(String objectName) {
        String ext = objectName.substring(objectName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "png" -> "image/png";
            case "pdf" -> "application/pdf";
            default -> "image/jpeg";
        };
    }
}
