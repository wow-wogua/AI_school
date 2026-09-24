package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Goods;
import com.aischool.server.entity.GoodsFlow;
import com.aischool.server.entity.User;
import com.aischool.server.entity.Venue;
import com.aischool.server.mapper.GoodsFlowMapper;
import com.aischool.server.mapper.GoodsMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.mapper.VenueMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.auth.PermissionService;
import com.aischool.server.service.oa.OaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端 OA（批9）：审批人配置（校方自助）+单据全量/代审+物资字典/入库/流水。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOaController {

    private final OaService oaService;
    private final GoodsMapper goodsMapper;
    private final GoodsFlowMapper goodsFlowMapper;
    private final UserMapper userMapper;
    private final PermissionService permissionService;

    // ---- 审批人配置 ----

    /** 审批人配置回显：id+姓名（空配置=null） */
    @GetMapping("/oa/config")
    public ApiResponse<Map<String, Object>> config() {
        permissionService.checkAdminAccess("只有管理员可配置审批人");
        Map<Long, String> names = approverNames();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sealApprovers", approverView("seal", names));
        out.put("goodsApprovers", approverView("goods", names));
        out.put("leaveApprovers", approverView("leave", names));
        out.put("venueApprovers", approverView("venue", names));
        out.put("goodsLevels", oaService.levels("GOODS"));
        out.put("leaveLevels", oaService.levels("LEAVE"));
        out.put("venueLevels", oaService.levels("VENUE"));
        return ApiResponse.ok(out);
    }

    private List<Map<String, Object>> approverView(String type, Map<Long, String> names) {
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            String v = oaService.cfgOf("oa_" + type + "_l" + i);
            if (v.isEmpty()) {
                list.add(null);
            } else {
                Long id = Long.parseLong(v);
                list.add(Map.of("id", id, "name", names.getOrDefault(id, "已注销账号")));
            }
        }
        return list;
    }

    /** 四型十二键涉及的审批人姓名 */
    private Map<Long, String> approverNames() {
        List<Long> ids = new java.util.ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            for (String type : List.of("seal", "goods", "leave", "venue")) {
                String v = oaService.cfgOf("oa_" + type + "_l" + i);
                if (!v.isEmpty()) {
                    ids.add(Long.parseLong(v));
                }
            }
        }
        return ids.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));
    }

    @PutMapping("/oa/config")
    public ApiResponse<Void> setConfig(@Validated @RequestBody ConfigReq req) {
        permissionService.checkAdminAccess("只有管理员可配置审批人");
        for (int i = 1; i <= 3; i++) {
            oaService.setCfg("oa_seal_l" + i, idStr(req.getSealApprovers(), i));
            oaService.setCfg("oa_goods_l" + i, idStr(req.getGoodsApprovers(), i));
            oaService.setCfg("oa_leave_l" + i, idStr(req.getLeaveApprovers(), i));
            oaService.setCfg("oa_venue_l" + i, idStr(req.getVenueApprovers(), i));
        }
        int goodsLevels = Math.max(1, Math.min(3, req.getGoodsLevels() == null ? 1 : req.getGoodsLevels()));
        oaService.setCfg("oa_goods_levels", String.valueOf(goodsLevels));
        int leaveLevels = Math.max(1, Math.min(3, req.getLeaveLevels() == null ? 1 : req.getLeaveLevels()));
        oaService.setCfg("oa_leave_levels", String.valueOf(leaveLevels));
        int venueLevels = Math.max(1, Math.min(3, req.getVenueLevels() == null ? 1 : req.getVenueLevels()));
        oaService.setCfg("oa_venue_levels", String.valueOf(venueLevels));
        return ApiResponse.ok();
    }

    private String idStr(List<Long> ids, int i) {
        if (ids == null || ids.size() < i || ids.get(i - 1) == null) {
            return "";
        }
        return String.valueOf(ids.get(i - 1));
    }

    /** 单据全量（管理端审计/代审入口） */
    @GetMapping("/oa/list")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String formType,
            @RequestParam(required = false) String status) {
        permissionService.checkAdminAccess("只有管理员可查单据");
        return ApiResponse.ok(oaService.allList(formType, status));
    }

    // ---- 场地字典（批11） ----

    private final VenueMapper venueMapper;

    @GetMapping("/venue")
    public ApiResponse<List<Map<String, Object>>> venue() {
        permissionService.checkAdminAccess("只有管理员可管理场地");
        return ApiResponse.ok(venueMapper.selectList(new LambdaQueryWrapper<Venue>().orderByAsc(Venue::getId))
                .stream().<Map<String, Object>>map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", v.getId());
                    m.put("name", v.getName());
                    m.put("location", v.getLocation() == null ? "" : v.getLocation());
                    m.put("capacity", v.getCapacity());
                    m.put("status", v.getStatus());
                    return m;
                }).toList());
    }

    /** 新增/编辑（id 空=新增；名称唯一） */
    @PostMapping("/venue")
    public ApiResponse<Void> saveVenue(@Validated @RequestBody VenueReq req) {
        permissionService.checkAdminAccess("只有管理员可管理场地");
        Venue v = req.getId() == null ? new Venue() : venueMapper.selectById(req.getId());
        if (v == null) {
            throw new BizException(404, "场地不存在");
        }
        v.setName(req.getName().trim());
        v.setLocation(req.getLocation());
        v.setCapacity(req.getCapacity());
        if (req.getId() == null) {
            v.setStatus(1);
        }
        try {
            if (req.getId() == null) {
                venueMapper.insert(v);
            } else {
                venueMapper.updateById(v);
            }
        } catch (DuplicateKeyException e) {
            throw new BizException(400, "已存在同名场地");
        }
        return ApiResponse.ok();
    }

    @PutMapping("/venue/{id}/status")
    public ApiResponse<Void> toggleVenue(@PathVariable Long id) {
        permissionService.checkAdminAccess("只有管理员可管理场地");
        Venue v = venueMapper.selectById(id);
        if (v == null) {
            throw new BizException(404, "场地不存在");
        }
        v.setStatus(v.getStatus() != null && v.getStatus() == 1 ? 0 : 1);
        venueMapper.updateById(v);
        return ApiResponse.ok();
    }

    @Data
    public static class VenueReq {
        private Long id;
        @NotBlank(message = "名称不能为空")
        private String name;
        private String location;
        private Integer capacity;
    }

    // ---- 物资字典 ----

    @GetMapping("/goods")
    public ApiResponse<List<Map<String, Object>>> goods() {
        permissionService.checkAdminAccess("只有管理员可管理物资");
        return ApiResponse.ok(goodsMapper.selectList(new LambdaQueryWrapper<Goods>().orderByAsc(Goods::getId))
                .stream().<Map<String, Object>>map(g -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", g.getId());
                    m.put("name", g.getName());
                    m.put("unit", g.getUnit());
                    m.put("stock", g.getStock());
                    m.put("location", g.getLocation() == null ? "" : g.getLocation());
                    m.put("status", g.getStatus());
                    return m;
                }).toList());
    }

    /** 新增/编辑（id 空=新增；名称唯一） */
    @PostMapping("/goods")
    public ApiResponse<Void> save(@Validated @RequestBody GoodsReq req) {
        permissionService.checkAdminAccess("只有管理员可管理物资");
        Goods g = req.getId() == null ? new Goods() : goodsMapper.selectById(req.getId());
        if (g == null) {
            throw new BizException(404, "物资不存在");
        }
        g.setName(req.getName().trim());
        g.setUnit(req.getUnit() == null || req.getUnit().isBlank() ? "件" : req.getUnit().trim());
        g.setLocation(req.getLocation());
        if (req.getId() == null) {
            g.setStock(0);
            g.setStatus(1);
        }
        try {
            if (req.getId() == null) {
                goodsMapper.insert(g);
            } else {
                goodsMapper.updateById(g);
            }
        } catch (DuplicateKeyException e) {
            throw new BizException(400, "已存在同名物资");
        }
        return ApiResponse.ok();
    }

    @PutMapping("/goods/{id}/status")
    public ApiResponse<Void> toggle(@PathVariable Long id) {
        permissionService.checkAdminAccess("只有管理员可管理物资");
        Goods g = goodsMapper.selectById(id);
        if (g == null) {
            throw new BizException(404, "物资不存在");
        }
        g.setStatus(g.getStatus() != null && g.getStatus() == 1 ? 0 : 1);
        goodsMapper.updateById(g);
        return ApiResponse.ok();
    }

    /** 入库（补库存）：stock+=qty + IN 流水 */
    @PostMapping("/goods/stock")
    public ApiResponse<Void> stockIn(@Validated @RequestBody StockReq req) {
        permissionService.checkAdminAccess("只有管理员可管理物资");
        Goods g = goodsMapper.selectById(req.getGoodsId());
        if (g == null) {
            throw new BizException(404, "物资不存在");
        }
        if (req.getQty() == null || req.getQty() <= 0) {
            throw new BizException(400, "入库数量须大于 0");
        }
        g.setStock(g.getStock() + req.getQty());
        goodsMapper.updateById(g);
        GoodsFlow flow = new GoodsFlow();
        flow.setGoodsId(g.getId());
        flow.setGoodsName(g.getName());
        flow.setQty(req.getQty());
        flow.setDirection(GoodsFlow.IN);
        flow.setLocation(g.getLocation());
        flow.setOperatorId(AuthUtil.current().userId());
        flow.setNote(req.getNote());
        goodsFlowMapper.insert(flow);
        return ApiResponse.ok();
    }

    /** 出入库流水（goodsId 可选筛选；OUT 行=谁/何时/哪里/拿走什么） */
    @GetMapping("/goods/flow")
    public ApiResponse<List<Map<String, Object>>> flow(@RequestParam(required = false) Long goodsId) {
        permissionService.checkAdminAccess("只有管理员可管理物资");
        List<GoodsFlow> rows = goodsFlowMapper.selectList(new LambdaQueryWrapper<GoodsFlow>()
                .eq(goodsId != null, GoodsFlow::getGoodsId, goodsId)
                .orderByDesc(GoodsFlow::getId).last("LIMIT 200"));
        Map<Long, String> names = rows.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(rows.stream()
                        .flatMap(r -> java.util.stream.Stream.of(r.getApplicantId(), r.getOperatorId()))
                        .filter(java.util.Objects::nonNull).distinct().toList())
                        .stream().collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));
        return ApiResponse.ok(rows.stream().<Map<String, Object>>map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("goodsName", r.getGoodsName());
            m.put("qty", r.getQty());
            m.put("unit", unitOf(r.getGoodsId()));
            m.put("direction", r.getDirection());
            m.put("location", r.getLocation() == null ? "" : r.getLocation());
            m.put("applicantName", r.getApplicantId() == null ? "" : names.getOrDefault(r.getApplicantId(), ""));
            m.put("operatorName", names.getOrDefault(r.getOperatorId(), ""));
            m.put("note", r.getNote() == null ? "" : r.getNote());
            m.put("createTime", r.getCreateTime());
            return m;
        }).toList());
    }

    private String unitOf(Long goodsId) {
        Goods g = goodsMapper.selectById(goodsId);
        return g == null ? "" : g.getUnit();
    }

    @Data
    public static class ConfigReq {
        private List<Long> sealApprovers; // [l1,l2,l3] 空位=null
        private Integer goodsLevels;
        private List<Long> goodsApprovers;
        private Integer leaveLevels; // 批10
        private List<Long> leaveApprovers;
        private Integer venueLevels; // 批11
        private List<Long> venueApprovers;
    }

    @Data
    public static class GoodsReq {
        private Long id;
        @NotBlank(message = "名称不能为空")
        private String name;
        private String unit;
        private String location;
    }

    @Data
    public static class StockReq {
        @NotNull(message = "goodsId 不能为空")
        private Long goodsId;
        @NotNull(message = "qty 不能为空")
        private Integer qty;
        private String note;
    }
}
