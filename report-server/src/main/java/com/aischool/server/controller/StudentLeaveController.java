package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.report.PdfStoreService;
import com.aischool.server.service.studentleave.StudentLeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 学生请假（批27）：家长替孩子提交+单级审批（任意一位教师批即生效）+门卫离校/返校登记。
 * 独立于 /api/oa（教职工口径）。GUARD 角色经 JwtAuthFilter 白名单仅可达本控制器与认证端点。
 */
@RestController
@RequestMapping("/api/student-leave")
@RequiredArgsConstructor
public class StudentLeaveController {

    private final StudentLeaveService service;
    private final PdfStoreService pdfStore;

    /** 家长提交（multipart：类型+起止+事由+照片≤3，同报修模式；日期 YYYY-MM-DD） */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestParam Long studentId,
                                                    @RequestParam String leaveType,
                                                    @RequestParam String startDate,
                                                    @RequestParam String endDate,
                                                    @RequestParam String reason,
                                                    @RequestParam(value = "photos", required = false) List<MultipartFile> photos) {
        var user = AuthUtil.current();
        if (!"PARENT".equals(user.role())) {
            throw new BizException(403, "学生请假由家长在家长端提交");
        }
        return ApiResponse.ok(service.create(user, studentId, leaveType,
                parseDate(startDate), parseDate(endDate), reason, photos));
    }

    /** 家长：我的请假记录 */
    @GetMapping("/my")
    public ApiResponse<List<Map<String, Object>>> my() {
        var user = AuthUtil.current();
        if (!"PARENT".equals(user.role())) {
            throw new BizException(403, "仅家长账号可查看自己的请假记录");
        }
        return ApiResponse.ok(service.my(user));
    }

    /** 家长撤回待审批请假单 */
    @PutMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        service.cancel(id, AuthUtil.current());
        return ApiResponse.ok();
    }

    /** 教师端列表：status 筛（PENDING/全部）；scope=my 本班（默认）/all 全校（任何老师可批口径） */
    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "my") String scope) {
        var user = AuthUtil.current();
        String role = user.role();
        if (!"TEACHER".equals(role) && !"HEAD_TEACHER".equals(role)
                && !"LEADER".equals(role) && !"ADMIN".equals(role)) {
            throw new BizException(403, "仅教师可查看学生请假列表");
        }
        return ApiResponse.ok(service.list(user, status, scope));
    }

    /** 批准（单级，任何一位教师/领导/管理员） */
    @PutMapping("/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id,
                                      @RequestParam(required = false) String note) {
        service.approve(id, AuthUtil.current(), note);
        return ApiResponse.ok();
    }

    /** 驳回（原因必填，缺参由服务层 400 带中文提示） */
    @PutMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id, @RequestParam(required = false) String note) {
        service.reject(id, AuthUtil.current(), note);
        return ApiResponse.ok();
    }

    /** 门卫核验视图：某日有效的已批准请假单（默认今天；q=学号/姓名/班级搜索） */
    @GetMapping("/guard")
    public ApiResponse<List<Map<String, Object>>> guard(@RequestParam(required = false) String date,
                                                         @RequestParam(required = false) String q) {
        return ApiResponse.ok(service.guardList(AuthUtil.current(),
                date == null || date.isBlank() ? null : parseDate(date), q));
    }

    /** 门卫登记离校 */
    @PutMapping("/{id}/leave")
    public ApiResponse<Void> registerLeave(@PathVariable Long id) {
        service.registerLeave(id, AuthUtil.current());
        return ApiResponse.ok();
    }

    /** 门卫登记返校 */
    @PutMapping("/{id}/return")
    public ApiResponse<Void> registerReturn(@PathVariable Long id) {
        service.registerReturn(id, AuthUtil.current());
        return ApiResponse.ok();
    }

    /** 详情（提交家长本人/教师侧/门卫） */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id, AuthUtil.current()));
    }

    /** 逐张凭证照片回图（inline，流式+ETag+缓存，同报修/微光模式） */
    @GetMapping("/file/{id}")
    public ResponseEntity<InputStreamResource> file(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "0") int idx) {
        String objectName = service.photoObject(id, idx, AuthUtil.current());
        InputStream in = pdfStore.download(objectName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentTypeOf(objectName)))
                .eTag("\"" + objectName + "\"")
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(new InputStreamResource(in));
    }

    private String contentTypeOf(String objectName) {
        String ext = objectName.substring(objectName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return ext.equals("png") ? "image/png" : "image/jpeg";
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw new BizException(400, "日期格式应为 YYYY-MM-DD");
        }
    }
}
