package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.ParentBinding;
import com.aischool.server.mapper.ParentBindingMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.auth.DataScopeService;
import com.aischool.server.service.timeline.TimelineService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 成长事件时间轴：评价/活动/荣誉/成绩进步统一事件流；lifecycle=在校全期档案 */
@RestController
@RequestMapping("/api/timeline")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService timelineService;
    private final DataScopeService dataScope;
    private final ParentBindingMapper bindingMapper;

    @GetMapping("/{studentId}")
    public ApiResponse<Map<String, Object>> events(@PathVariable Long studentId, @RequestParam Long termId) {
        dataScope.checkStudentAccess(AuthUtil.current(), studentId);
        return ApiResponse.ok(timelineService.events(studentId, termId));
    }

    /** 生命周期档案（批14）：家长=绑定孩子可看，教师侧沿用 checkStudentAccess */
    @GetMapping("/{studentId}/lifecycle")
    public ApiResponse<Map<String, Object>> lifecycle(@PathVariable Long studentId) {
        if ("PARENT".equals(AuthUtil.current().role())) {
            requireBound(studentId);
        } else {
            dataScope.checkStudentAccess(AuthUtil.current(), studentId);
        }
        return ApiResponse.ok(timelineService.lifecycle(studentId));
    }

    private void requireBound(Long studentId) {
        Long count = bindingMapper.selectCount(new LambdaQueryWrapper<ParentBinding>()
                .eq(ParentBinding::getParentUserId, AuthUtil.current().userId())
                .eq(ParentBinding::getStudentId, studentId));
        if (count == 0) {
            throw new BizException(403, "该学生未绑定当前家长账号");
        }
    }
}
