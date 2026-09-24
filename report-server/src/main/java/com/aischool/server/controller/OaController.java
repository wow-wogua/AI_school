package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Goods;
import com.aischool.server.mapper.GoodsMapper;
import com.aischool.server.service.oa.OaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 行政办公审批（批9）：公章使用申请（三级）/物资申领（级数可配）。
 * 教师/班主任/领导发起；审批人由管理端配置（ADMIN 可代审，见 OaService.handle）。
 */
@RestController
@RequestMapping("/api/oa")
@RequiredArgsConstructor
public class OaController {

    private final OaService oaService;
    private final GoodsMapper goodsMapper;

    @PostMapping("/submit")
    public ApiResponse<Map<String, Object>> submit(@RequestBody OaService.SubmitReq req) {
        return ApiResponse.ok(rowOf(oaService.submit(req)));
    }

    /** 我的申请（status 可选筛选） */
    @GetMapping("/my")
    public ApiResponse<List<Map<String, Object>>> my(@RequestParam(required = false) String status) {
        return ApiResponse.ok(oaService.myList(status));
    }

    /** 待我审批（当前节点审批人=本人） */
    @GetMapping("/todo")
    public ApiResponse<List<Map<String, Object>>> todo() {
        return ApiResponse.ok(oaService.todoList());
    }

    /** 单据详情（申请人与当前审批人可见；ADMIN 全量） */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(oaService.detail(id));
    }

    /** 审批动作：AGREE/REJECT（当前级审批人或 ADMIN）；REVOKE 仅申请人本人 */
    @PostMapping("/{id}/handle")
    public ApiResponse<Void> handle(@PathVariable Long id, @RequestBody OaService.HandleReq req) {
        oaService.handle(id, req);
        return ApiResponse.ok();
    }

    /** 发起页物资下拉（可申领字典；库存实时） */
    @GetMapping("/goods")
    public ApiResponse<List<Map<String, Object>>> goods() {
        if ("PARENT".equals(com.aischool.server.security.AuthUtil.current().role())) {
            throw new BizException(403, "家长账号无需申领物资");
        }
        List<Goods> rows = goodsMapper.selectList(new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 1).orderByAsc(Goods::getName));
        return ApiResponse.ok(rows.stream().<Map<String, Object>>map(g -> Map.of(
                "id", g.getId(),
                "name", g.getName(),
                "unit", g.getUnit(),
                "stock", g.getStock(),
                "location", g.getLocation() == null ? "" : g.getLocation())).toList());
    }

    private Map<String, Object> rowOf(com.aischool.server.entity.OaForm f) {
        return Map.of("id", f.getId(), "status", f.getStatus(), "title", f.getTitle());
    }
}
