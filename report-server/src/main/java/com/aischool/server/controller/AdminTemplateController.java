package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.ReportTemplate;
import com.aischool.server.mapper.ReportTemplateMapper;
import com.aischool.server.service.auth.PermissionService;
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
    private final PermissionService permissionService;

    private final ReportTemplateMapper templateMapper;
    private final ObjectMapper objectMapper;

    private void checkAdmin() {
        permissionService.checkAdminAccess("只有管理员可操作系统管理");
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

    // ==================== 常用文案（批35 学校自治）====================
    // 文案字段（校名/简介/九格介绍/格言/理念）不属契约结构，白名单豁免锁定：
    // 学校在「报告模板 → 常用文案」表单直改生效模板，无需编辑 JSON。

    @GetMapping("/template/copy")
    public ApiResponse<Map<String, Object>> copy() {
        checkAdmin();
        ReportTemplate t = enabledTemplate();
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        try {
            var sections = objectMapper.readTree(t.getSections());
            var radar = sections.path("radar");
            m.put("schoolName", t.getSchoolName());
            m.put("intro", sections.path("intro").asText(""));
            m.put("nineGridIntro", sections.path("nineGridIntro").asText(""));
            m.put("motto", radar.path("motto").asText(""));
            m.put("mottoNote", radar.path("mottoNote").asText(""));
            m.put("mottoSource", radar.path("mottoSource").asText(""));
            List<List<String>> philosophy = new java.util.ArrayList<>();
            sections.path("philosophy").forEach(pair -> {
                List<String> p = new java.util.ArrayList<>();
                pair.forEach(v -> p.add(v.asText()));
                philosophy.add(p);
            });
            m.put("philosophy", philosophy);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "模板解析失败: " + e.getMessage());
        }
        return ApiResponse.ok(m);
    }

    @PutMapping("/template/copy")
    public ApiResponse<Void> updateCopy(@RequestBody CopyReq req) {
        checkAdmin();
        ReportTemplate t = enabledTemplate();
        if (req.getSchoolName() == null || req.getSchoolName().isBlank()) {
            throw new BizException(400, "学校名不能为空");
        }
        if (req.getPhilosophy() == null || req.getPhilosophy().isEmpty()
                || req.getPhilosophy().size() > 8) {
            throw new BizException(400, "办学理念须 1~8 行");
        }
        for (List<String> pair : req.getPhilosophy()) {
            if (pair == null || pair.size() != 2 || pair.get(0).isBlank() || pair.get(1).isBlank()) {
                throw new BizException(400, "办学理念每行须为「名称 + 说明」且不能为空");
            }
        }
        try {
            var sections = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(t.getSections());
            sections.put("intro", req.getIntro() == null ? "" : req.getIntro());
            sections.put("nineGridIntro", req.getNineGridIntro() == null ? "" : req.getNineGridIntro());
            var radar = (com.fasterxml.jackson.databind.node.ObjectNode) sections.with("radar");
            radar.put("motto", req.getMotto() == null ? "" : req.getMotto());
            radar.put("mottoNote", req.getMottoNote() == null ? "" : req.getMottoNote());
            radar.put("mottoSource", req.getMottoSource() == null ? "" : req.getMottoSource());
            var arr = objectMapper.createArrayNode();
            for (List<String> pair : req.getPhilosophy()) {
                arr.addArray().add(pair.get(0)).add(pair.get(1));
            }
            sections.set("philosophy", arr);
            templateMapper.update(null, new LambdaUpdateWrapper<ReportTemplate>()
                    .eq(ReportTemplate::getId, t.getId())
                    .set(ReportTemplate::getSchoolName, req.getSchoolName().trim())
                    .set(ReportTemplate::getSections, objectMapper.writeValueAsString(sections))
                    .set(ReportTemplate::getUpdateTime, LocalDateTime.now()));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "模板保存失败: " + e.getMessage());
        }
        return ApiResponse.ok();
    }

    private ReportTemplate enabledTemplate() {
        ReportTemplate t = templateMapper.selectOne(new LambdaQueryWrapper<ReportTemplate>()
                .eq(ReportTemplate::getStatus, STATUS_ON).last("LIMIT 1"));
        if (t == null) {
            throw new BizException(404, "无启用模板");
        }
        return t;
    }

    @Data
    public static class CopyReq {
        private String schoolName;
        private String intro;
        private String nineGridIntro;
        private String motto;
        private String mottoNote;
        private String mottoSource;
        private List<List<String>> philosophy;
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
