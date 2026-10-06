package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.SysConfig;
import com.aischool.server.mapper.SysConfigMapper;
import com.aischool.server.service.auth.PermissionService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端「系统参数」（批35 学校自治）：t_sys_config 常用参数表单化。
 * 当前覆盖学生请假分级阈值（leave_level1_days / leave_level2_days / leave_max_days，
 * 消费方 StudentLeaveService 动态读库，改完即时生效）。
 */
@RestController
@RequestMapping("/api/admin/sys-param")
@RequiredArgsConstructor
public class AdminSysParamController {

    private final SysConfigMapper sysConfigMapper;
    private final PermissionService permissionService;

    private void checkAdmin() {
        permissionService.checkAdminAccess("只有管理员可操作系统管理");
    }

    private int cfgInt(String key, int dft) {
        SysConfig c = sysConfigMapper.selectById(key);
        if (c == null || c.getCfgValue() == null) return dft;
        try {
            return Integer.parseInt(c.getCfgValue().trim());
        } catch (NumberFormatException e) {
            return dft;
        }
    }

    private void set(String key, int value) {
        int n = sysConfigMapper.update(null, new LambdaUpdateWrapper<SysConfig>()
                .eq(SysConfig::getCfgKey, key).set(SysConfig::getCfgValue, String.valueOf(value)));
        if (n == 0) {
            SysConfig c = new SysConfig();
            c.setCfgKey(key);
            c.setCfgValue(String.valueOf(value));
            sysConfigMapper.insert(c);
        }
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> get() {
        checkAdmin();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("leaveLevel1Days", cfgInt("leave_level1_days", 3));
        m.put("leaveLevel2Days", cfgInt("leave_level2_days", 7));
        m.put("leaveMaxDays", cfgInt("leave_max_days", 30));
        return ApiResponse.ok(m);
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> set(@Validated @RequestBody ParamReq req) {
        checkAdmin();
        int l1 = req.getLeaveLevel1Days() == null ? 3 : req.getLeaveLevel1Days();
        int l2 = req.getLeaveLevel2Days() == null ? 7 : req.getLeaveLevel2Days();
        int max = req.getLeaveMaxDays() == null ? 30 : req.getLeaveMaxDays();
        if (l1 < 1 || l2 <= l1 || max < l2 || max > 365) {
            throw new BizException(400, "阈值须满足 1 ≤ 即生效天数 < 级长审批天数 ≤ 上限天数 ≤ 365");
        }
        set("leave_level1_days", l1);
        set("leave_level2_days", l2);
        set("leave_max_days", max);
        return get();
    }

    @Data
    public static class ParamReq {
        private Integer leaveLevel1Days;
        private Integer leaveLevel2Days;
        private Integer leaveMaxDays;
    }
}
