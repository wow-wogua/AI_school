package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.ConductAccount;
import com.aischool.server.entity.ConductLog;
import com.aischool.server.entity.ConductRule;
import com.aischool.server.mapper.ConductAccountMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.auth.DataScopeService;
import com.aischool.server.service.conduct.ConductLedgerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 操行分账本（批3）：教师端查询/手动调整；权限口径同评价（checkStudentAccess）。
 */
@RestController
@RequestMapping("/api/conduct")
@RequiredArgsConstructor
public class ConductController {

    private final ConductLedgerService conductLedger;
    private final ConductAccountMapper conductAccountMapper;
    private final DataScopeService dataScope;

    /** 学生操行分概览：余额+等级+本学期流水（未初始化=基础分起点、无流水） */
    @GetMapping("/student/{studentId}")
    public ApiResponse<Map<String, Object>> student(@PathVariable Long studentId) {
        dataScope.checkStudentAccess(AuthUtil.current(), studentId);
        ConductRule rule = conductLedger.getRule();
        Long termId = conductLedger.resolveTermId(LocalDate.now());
        ConductAccount account = conductAccountMapper.selectOne(new LambdaQueryWrapper<ConductAccount>()
                .eq(ConductAccount::getStudentId, studentId)
                .eq(ConductAccount::getTermId, termId)
                .last("LIMIT 1"));
        BigDecimal balance = account != null ? account.getBalance() : rule.getBaseScore();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("balance", balance);
        m.put("grade", conductLedger.gradeOf(balance, rule));
        m.put("initialized", account != null);
        m.put("rule", ruleOf(rule));
        m.put("logs", conductLedger.logsOf(studentId, termId));
        return ApiResponse.ok(m);
    }

    /** 手动调整（奖/惩）：delta≠0、理由必填 */
    @PostMapping("/adjust")
    public ApiResponse<Void> adjust(@Validated @RequestBody AdjustReq req) {
        dataScope.checkStudentAccess(AuthUtil.current(), req.getStudentId());
        if (req.getDelta() == null || req.getDelta().compareTo(BigDecimal.ZERO) == 0) {
            throw new BizException(400, "调整值不能为 0");
        }
        if (req.getReason() == null || req.getReason().isBlank()) {
            throw new BizException(400, "调整理由不能为空");
        }
        conductLedger.apply(req.getStudentId(), LocalDate.now(), ConductLog.SRC_MANUAL, null,
                req.getDelta(), req.getReason().trim(), AuthUtil.current().userId());
        return ApiResponse.ok();
    }

    /** 等级线回显（教师端展示口径）；家长仅经 /api/parent 钱包内嵌获取 */
    @GetMapping("/rule")
    public ApiResponse<Map<String, Object>> rule() {
        if ("PARENT".equals(AuthUtil.current().role())) {
            throw new BizException(403, "仅教师可访问");
        }
        return ApiResponse.ok(ruleOf(conductLedger.getRule()));
    }

    private Map<String, Object> ruleOf(ConductRule rule) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("baseScore", rule.getBaseScore());
        m.put("gradeAMin", rule.getGradeAMin());
        m.put("gradeBMin", rule.getGradeBMin());
        m.put("gradeCMin", rule.getGradeCMin());
        return m;
    }

    @Data
    public static class AdjustReq {
        @NotNull(message = "studentId 不能为空")
        private Long studentId;
        @NotNull(message = "delta 不能为空")
        private BigDecimal delta;
        private String reason;
    }
}
