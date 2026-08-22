package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.ReportTemplate;
import com.aischool.server.mapper.ReportTemplateMapper;
import com.aischool.server.security.AuthUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 管理端：报告模板（仅管理员）。
 * 启用模板是契约验证（学生1报告）的基线，锁定只读：改任何字段/删除/状态切换一律 400；
 * 草稿模板可自由增删改，但不提供启用切换（启用即改契约，需 DBA 介入）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminTemplateController {

    private static final String STATUS_ON = "启用";
    private static final String STATUS_DRAFT = "草稿";

    private final ReportTemplateMapper templateMapper;
    private final ObjectMapper objectMapper;

    private void checkAdmin() {
        if (!"ADMIN".equals(AuthUtil.current().role())) {
            throw new BizException(403, "只有管理员可操作系统管理");
        }
    }

    @GetMapping("/template/list")
    public ApiResponse<List<ReportTemplate>> templateList() {
        checkAdmin();
        return ApiResponse.ok(templateMapper.selectList(
                new LambdaQueryWrapper<ReportTemplate>().orderByAsc(ReportTemplate::getId)));
    }

    @Data
    public static class TemplateReq {
        @NotBlank(message = "schoolName 不能为空")
        private String schoolName;
        @NotBlank(message = "sections 不能为空")
        private String sections;
    }

    @PostMapping("/template")
    public ApiResponse<Map<String, Object>> createTemplate(@Validated @RequestBody TemplateReq req) {
        checkAdmin();
        validateSections(req.getSections());
        ReportTemplate t = new ReportTemplate();
        t.setSchoolName(req.getSchoolName());
        t.setSections(req.getSections());
        t.setStatus(STATUS_DRAFT);
        t.setCreateTime(LocalDateTime.now());
        templateMapper.insert(t);
        return ApiResponse.ok(Map.of("templateId", t.getId()));
    }

    @PutMapping("/template/{id}")
    public ApiResponse<Void> updateTemplate(@PathVariable Long id, @Validated @RequestBody TemplateReq req) {
        checkAdmin();
        ReportTemplate t = requireDraft(id);
        validateSections(req.getSections());
        templateMapper.update(null, new LambdaUpdateWrapper<ReportTemplate>()
                .eq(ReportTemplate::getId, id)
                .set(ReportTemplate::getSchoolName, req.getSchoolName())
                .set(ReportTemplate::getSections, req.getSections())
                .set(ReportTemplate::getUpdateTime, LocalDateTime.now()));
        return ApiResponse.ok();
    }

    @DeleteMapping("/template/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        checkAdmin();
        requireDraft(id);
        templateMapper.deleteById(id);
        return ApiResponse.ok();
    }

    /** 状态切换一律拒绝：启用模板锁定，草稿不能自助转启用（契约基线变更需 DBA） */
    @PutMapping("/template/{id}/status")
    public ApiResponse<Void> switchStatus(@PathVariable Long id) {
        checkAdmin();
        ReportTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BizException(404, "模板不存在");
        }
        if (STATUS_ON.equals(t.getStatus())) {
            throw new BizException(400, "启用模板为契约基线（学生1契约验证），锁定只读");
        }
        throw new BizException(400, "草稿模板不能自助启用（启用即变更契约基线），需 DBA 介入");
    }

    private ReportTemplate requireDraft(Long id) {
        ReportTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BizException(404, "模板不存在");
        }
        if (STATUS_ON.equals(t.getStatus())) {
            throw new BizException(400, "启用模板为契约基线（学生1契约验证），锁定只读");
        }
        return t;
    }

    /** sections 必须是合法 JSON 对象 */
    private void validateSections(String sections) {
        try {
            var node = objectMapper.readTree(sections);
            if (!node.isObject()) {
                throw new BizException(400, "sections 必须是 JSON 对象");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(400, "sections 不是合法 JSON: " + e.getMessage());
        }
    }
}
