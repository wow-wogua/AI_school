package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Evaluation;
import com.aischool.server.entity.Grid;
import com.aischool.server.entity.Indicator;
import com.aischool.server.mapper.EvaluationMapper;
import com.aischool.server.mapper.GridMapper;
import com.aischool.server.mapper.IndicatorMapper;
import com.aischool.server.security.AuthUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端：育人指标体系（仅管理员）。
 * 九维本体（t_grid）只读——报告渲染与契约验证依赖格定义；
 * 二级指标可增改删，但被评价记录引用时禁止改名/删除（改名会改写既有报告的记录卡分组）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminIndicatorController {

    private final GridMapper gridMapper;
    private final IndicatorMapper indicatorMapper;
    private final EvaluationMapper evaluationMapper;

    private void checkAdmin() {
        if (!"ADMIN".equals(AuthUtil.current().role())) {
            throw new BizException(403, "只有管理员可操作系统管理");
        }
    }

    /** 九维列表（只读，附各格指标数） */
    @GetMapping("/grid/list")
    public ApiResponse<List<Map<String, Object>>> gridList() {
        checkAdmin();
        List<Grid> grids = gridMapper.selectList(
                new LambdaQueryWrapper<Grid>().orderByAsc(Grid::getSort));
        List<Indicator> all = indicatorMapper.selectList(null);
        Map<Long, Long> counts = all.stream().filter(i -> i.getName() != null && !i.getName().isEmpty())
                .collect(Collectors.groupingBy(Indicator::getGridId, Collectors.counting()));
        return ApiResponse.ok(grids.stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId());
            m.put("code", g.getCode());
            m.put("name", g.getName());
            m.put("icon", g.getIcon());
            m.put("sort", g.getSort());
            m.put("indicatorCount", counts.getOrDefault(g.getId(), 0L));
            return m;
        }).toList());
    }

    /** 指标列表（gridId 可选；含种子占位空名指标供管理端辨认） */
    @GetMapping("/indicator/list")
    public ApiResponse<List<Indicator>> indicatorList(@RequestParam(required = false) Long gridId) {
        checkAdmin();
        return ApiResponse.ok(indicatorMapper.selectList(new LambdaQueryWrapper<Indicator>()
                .eq(gridId != null, Indicator::getGridId, gridId)
                .orderByAsc(Indicator::getId)));
    }

    @Data
    public static class IndicatorReq {
        @NotNull(message = "gridId 不能为空")
        private Long gridId;
        @NotBlank(message = "name 不能为空")
        private String name;
        private String direction;
        private BigDecimal defaultScore;
        private String subjectScope;
    }

    @PostMapping("/indicator")
    public ApiResponse<Map<String, Object>> createIndicator(@Validated @RequestBody IndicatorReq req) {
        checkAdmin();
        if (gridMapper.selectById(req.getGridId()) == null) {
            throw new BizException(404, "九维不存在");
        }
        Indicator i = new Indicator();
        copy(req, i);
        indicatorMapper.insert(i);
        return ApiResponse.ok(Map.of("indicatorId", i.getId()));
    }

    @PutMapping("/indicator/{id}")
    public ApiResponse<Void> updateIndicator(@PathVariable Long id, @Validated @RequestBody IndicatorReq req) {
        checkAdmin();
        Indicator i = indicatorMapper.selectById(id);
        if (i == null) {
            throw new BizException(404, "指标不存在");
        }
        if (gridMapper.selectById(req.getGridId()) == null) {
            throw new BizException(404, "九维不存在");
        }
        // 改名守卫：被评价引用的指标改名会改写既有报告的记录卡分组
        boolean renamed = !req.getName().equals(i.getName());
        if (renamed && evaluationMapper.selectCount(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getIndicatorId, id)) > 0) {
            throw new BizException(400, "该指标已被评价记录引用，改名会改写既有报告分组");
        }
        boolean movedGrid = !req.getGridId().equals(i.getGridId());
        if (movedGrid && evaluationMapper.selectCount(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getIndicatorId, id)) > 0) {
            throw new BizException(400, "该指标已被评价记录引用，不可移动到其他格");
        }
        copy(req, i);
        indicatorMapper.updateById(i);
        return ApiResponse.ok();
    }

    @DeleteMapping("/indicator/{id}")
    public ApiResponse<Void> deleteIndicator(@PathVariable Long id) {
        checkAdmin();
        if (indicatorMapper.selectById(id) == null) {
            throw new BizException(404, "指标不存在");
        }
        if (evaluationMapper.selectCount(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getIndicatorId, id)) > 0) {
            throw new BizException(400, "该指标已被评价记录引用，不可删除");
        }
        indicatorMapper.deleteById(id);
        return ApiResponse.ok();
    }

    private void copy(IndicatorReq req, Indicator i) {
        i.setGridId(req.getGridId());
        i.setName(req.getName());
        i.setDirection(req.getDirection() == null || req.getDirection().isBlank() ? "+" : req.getDirection());
        i.setDefaultScore(req.getDefaultScore());
        i.setSubjectScope(req.getSubjectScope());
    }
}
