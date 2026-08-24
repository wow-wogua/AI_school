package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.Grade;
import com.aischool.server.entity.Grid;
import com.aischool.server.entity.Indicator;
import com.aischool.server.entity.Subject;
import com.aischool.server.entity.Term;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.GradeMapper;
import com.aischool.server.mapper.GridMapper;
import com.aischool.server.mapper.IndicatorMapper;
import com.aischool.server.mapper.SubjectMapper;
import com.aischool.server.mapper.TermMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.auth.DataScopeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 下拉数据：学期 / 我的班级（按数据权限过滤）/ 年级 / 九维 / 二级指标 */
@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {

    private final TermMapper termMapper;
    private final ClazzMapper clazzMapper;
    private final GradeMapper gradeMapper;
    private final GridMapper gridMapper;
    private final IndicatorMapper indicatorMapper;
    private final SubjectMapper subjectMapper;
    private final DataScopeService dataScopeService;

    @GetMapping("/terms")
    public ApiResponse<List<Term>> terms() {
        return ApiResponse.ok(termMapper.selectList(
                new LambdaQueryWrapper<Term>().orderByDesc(Term::getIsCurrent).orderByDesc(Term::getId)));
    }

    @GetMapping("/grades")
    public ApiResponse<List<Grade>> grades() {
        return ApiResponse.ok(gradeMapper.selectList(
                new LambdaQueryWrapper<Grade>().orderByAsc(Grade::getId)));
    }

    @GetMapping("/grids")
    public ApiResponse<List<Grid>> grids() {
        return ApiResponse.ok(gridMapper.selectList(
                new LambdaQueryWrapper<Grid>().orderByAsc(Grid::getSort)));
    }

    /** 评价录入用二级指标（过滤种子占位的空名指标） */
    @GetMapping("/indicators")
    public ApiResponse<List<Indicator>> indicators(@RequestParam(required = false) Long gridId) {
        return ApiResponse.ok(indicatorMapper.selectList(new LambdaQueryWrapper<Indicator>()
                .eq(gridId != null, Indicator::getGridId, gridId)
                .ne(Indicator::getName, "")
                .orderByAsc(Indicator::getId)));
    }

    /** 科目（建考试 / 任课关系选择用） */
    @GetMapping("/subjects")
    public ApiResponse<List<Subject>> subjects() {
        return ApiResponse.ok(subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>().orderByAsc(Subject::getSort)));
    }

    @GetMapping("/my-classes")
    public ApiResponse<List<Map<String, Object>>> myClasses() {
        var user = AuthUtil.current();
        List<Long> visible = dataScopeService.visibleClassIds(user);
        List<Clazz> list = clazzMapper.selectList(new LambdaQueryWrapper<Clazz>()
                .in(visible != null, Clazz::getId, visible != null ? visible : List.of(-1L))
                .orderByAsc(Clazz::getId));
        return ApiResponse.ok(list.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("gradeId", c.getGradeId());
            return m;
        }).toList());
    }
}
