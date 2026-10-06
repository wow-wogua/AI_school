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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 学生请假（批32 重构）：家长不在 App 提交（微信/电话告知班主任，由教师代录 creator），家长只读+收通知；
 * 按时长分级审批（≤3天录入即生效 / 3~7天级长 / 7~30天级长+学成中心主任 / 超限走纸质），
 * 批准后门卫+生活老师自动抄送；任课教师不可见。独立于 /api/oa（教职工口径）。
 * GUARD 角色经 JwtAuthFilter 白名单仅可达本控制器与认证端点。
 */
@RestController
@RequestMapping("/api/student-leave")
@RequiredArgsConstructor
public class StudentLeaveController {

    private final StudentLeaveService service;
    private final PdfStoreService pdfStore;

    /** 教师代录（multipart：学生+类型+起止时间（yyyy-MM-dd HH:mm）+事由+照片≤3）；角色/范围门在服务层 */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestParam Long studentId,
                                                    @RequestParam String leaveType,
                                                    @RequestParam String startTime,
                                                    @RequestParam String endTime,
                                                    @RequestParam String reason,
                                                    @RequestParam(value = "photos", required = false) List<MultipartFile> photos) {
        return ApiResponse.ok(service.create(AuthUtil.current(), studentId, leaveType,
                parseDateTime(startTime), parseDateTime(endTime), reason, photos));
    }

    /** 录入表单：管辖范围内班级列表 */
    @GetMapping("/classes")
    public ApiResponse<List<Map<String, Object>>> classes() {
        return ApiResponse.ok(service.classes(AuthUtil.current()));
    }

    /** 录入表单：班级内学生（q=学号/姓名可搜） */
    @GetMapping("/students")
    public ApiResponse<List<Map<String, Object>>> students(@RequestParam Long classId,
                                                            @RequestParam(required = false) String q) {
        return ApiResponse.ok(service.students(AuthUtil.current(), classId, q));
    }

    /** 家长只读：绑定孩子的请假记录（含回执/结果通知对应单据） */
    @GetMapping("/my")
    public ApiResponse<List<Map<String, Object>>> my() {
        var user = AuthUtil.current();
        if (!"PARENT".equals(user.role())) {
            throw new BizException(403, "仅家长账号可查看孩子的请假记录");
        }
        return ApiResponse.ok(service.my(user));
    }

    /** 撤销待审批请假单（发起教师或主任/领导/管理员，服务层校验） */
    @PutMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        service.cancel(id, AuthUtil.current());
        return ApiResponse.ok();
    }

    /** 教师端列表：status 筛（PENDING/APPROVED/REJECTED/CANCELLED/全部）；scope=my 管辖范围（默认）/all 全校（仅主任/领导/管理员） */
    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "my") String scope) {
        return ApiResponse.ok(service.list(AuthUtil.current(), status, scope));
    }

    /** 通过当前审批级（服务层按 current_step 校验审批人） */
    @PutMapping("/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id,
                                      @RequestParam(required = false) String note) {
        service.approve(id, AuthUtil.current(), note);
        return ApiResponse.ok();
    }

    /** 驳回（终态，原因必填，缺参由服务层 400 带中文提示） */
    @PutMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id,
                                      @RequestParam(required = false) String note) {
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

    /** 详情（含流转时间线；绑定家长/教师侧各角色/门卫可读） */
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

    /** 兼容 yyyy-MM-dd HH:mm（el-date-picker）与 ISO yyyy-MM-ddTHH:mm */
    private static LocalDateTime parseDateTime(String s) {
        if (s != null && s.contains("T")) {
            try {
                return LocalDateTime.parse(s);
            } catch (Exception e) {
                // 走下面的格式化解析
            }
        }
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception e) {
            throw new BizException(400, "时间格式应为 YYYY-MM-DD HH:mm");
        }
    }
}
