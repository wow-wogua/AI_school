package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.entity.Student;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.auth.DataScopeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aischool.server.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 学生选择（发起报告生成用）：按数据权限过滤 */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentMapper studentMapper;
    private final DataScopeService dataScopeService;

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        var user = AuthUtil.current();
        List<Long> visible = dataScopeService.visibleClassIds(user);

        LambdaQueryWrapper<Student> qw = new LambdaQueryWrapper<Student>()
                .eq(classId != null, Student::getClassId, classId)
                .like(keyword != null && !keyword.isBlank(), Student::getName, keyword)
                .in(visible != null, Student::getClassId, visible != null ? visible : List.of(-1L))
                .orderByAsc(Student::getStudentNo);
        Page<Student> p = studentMapper.selectPage(Page.of(page, Math.min(size, 100)), qw);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", p.getTotal());
        m.put("records", p.getRecords().stream().map(s -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", s.getId());
            r.put("studentNo", s.getStudentNo());
            r.put("name", s.getName());
            r.put("gender", s.getGender());
            r.put("classId", s.getClassId());
            r.put("status", s.getStatus());
            return r;
        }).toList());
        return ApiResponse.ok(m);
    }
}
