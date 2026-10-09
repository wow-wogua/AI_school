package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.Grade;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.GradeMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.auth.PermissionService;
import com.aischool.server.service.civility.CivilityService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文明班评比 B 案（批30）：教师端打分+排名；评选冻结/删除走管理权限。
 * 打分参照条目=德育规范静态页（/conduct-rules，conductRules.ts），本接口不依赖条目表（文本快照入库）。
 */
@RestController
@RequestMapping("/api/civility")
@RequiredArgsConstructor
public class CivilityController {

    private final CivilityService civilityService;
    private final ClazzMapper clazzMapper;
    private final GradeMapper gradeMapper;
    private final PermissionService permissionService;

    /** 全部班级+年级名（打分面向全校班级，值日巡查不限自己班） */
    @GetMapping("/classes")
    public ApiResponse<List<Map<String, Object>>> classes() {
        rejectParentGuard();
        Map<Long, String> gradeNames = gradeMapper.selectList(null).stream()
                .collect(Collectors.toMap(Grade::getId, Grade::getName, (a, b) -> a));
        return ApiResponse.ok(clazzMapper.selectList(new LambdaQueryWrapper<Clazz>().orderByAsc(Clazz::getId))
                .stream().<Map<String, Object>>map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getName());
                    m.put("gradeId", c.getGradeId());
                    m.put("gradeName", gradeNames.getOrDefault(c.getGradeId(), ""));
                    return m;
                }).toList());
    }

    @PostMapping("/score")
    public ApiResponse<Map<String, Object>> score(@Validated @RequestBody CivilityService.ScoreReq req) {
        return ApiResponse.ok(civilityService.create(req));
    }

    @GetMapping("/records")
    public ApiResponse<List<Map<String, Object>>> records(@RequestParam(required = false) Long classId,
                                                          @RequestParam(required = false) String from,
                                                          @RequestParam(required = false) String to) {
        rejectParentGuard();
        return ApiResponse.ok(civilityService.records(classId, parseDate(from, "from"), parseDate(to, "to")));
    }

    @DeleteMapping("/score/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionService.checkAdminAccess("仅管理员可删除打分记录");
        civilityService.delete(id);
        return ApiResponse.ok();
    }

    /** 自动汇总排名：from/to 必填（yyyy-MM-dd），gradeId 可选 */
    @GetMapping("/rank")
    public ApiResponse<Map<String, Object>> rank(@RequestParam String from, @RequestParam String to,
                                                 @RequestParam(required = false) Long gradeId) {
        rejectParentGuard();
        return ApiResponse.ok(civilityService.rank(parseDate(from, "from"), parseDate(to, "to"), gradeId));
    }

    /** 评选（按月冻结快照；重评覆盖） */
    @PostMapping("/settle")
    public ApiResponse<List<Map<String, Object>>> settle(@Validated @RequestBody SettleReq req) {
        permissionService.checkAdminAccess("仅管理员可发起文明班评选");
        return ApiResponse.ok(civilityService.settle(req.getMonth().trim(), AuthUtil.current().userId()));
    }

    @GetMapping("/awards")
    public ApiResponse<List<Map<String, Object>>> awards(@RequestParam(required = false) String month) {
        rejectParentGuard();
        return ApiResponse.ok(civilityService.awards(month));
    }

    // ───────── 批43：班级整体加减分（素养评价双轨之一，进文明班不进个人档案） ─────────

    /** 可记分班级下拉（班主任=本班；级长=绑定年级；主任/领导/管理员=全校；无权限 403 供前端隐藏入口） */
    @GetMapping("/class-score/classes")
    public ApiResponse<List<Map<String, Object>>> classScoreClasses() {
        rejectParentGuard();
        return ApiResponse.ok(civilityService.classScoreClasses());
    }

    @PostMapping("/class-score")
    public ApiResponse<Map<String, Object>> createClassScore(@Validated @RequestBody CivilityService.ClassScoreReq req) {
        rejectParentGuard();
        return ApiResponse.ok(civilityService.createClassScore(req));
    }

    @GetMapping("/class-score/list")
    public ApiResponse<List<Map<String, Object>>> classScoreList(@RequestParam Long classId,
                                                                 @RequestParam(required = false) String from,
                                                                 @RequestParam(required = false) String to) {
        rejectParentGuard();
        return ApiResponse.ok(civilityService.classScoreList(classId,
                parseDate(from, "from"), parseDate(to, "to")));
    }

    @DeleteMapping("/class-score/{id}")
    public ApiResponse<Void> deleteClassScore(@PathVariable Long id) {
        rejectParentGuard();
        civilityService.deleteClassScore(id);
        return ApiResponse.ok();
    }

    private void rejectParentGuard() {
        String role = AuthUtil.current().role();
        if ("PARENT".equals(role) || "GUARD".equals(role)) {
            throw new BizException(403, "仅教师可访问文明班评比");
        }
    }

    private LocalDate parseDate(String v, String field) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new BizException(400, field + " 日期格式须为 yyyy-MM-dd");
        }
    }

    @Data
    public static class SettleReq {
        @NotBlank(message = "month 不能为空")
        private String month; // yyyy-MM
    }
}
