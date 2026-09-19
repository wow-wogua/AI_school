package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.CoinExpense;
import com.aischool.server.entity.ShopItem;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.CoinExpenseMapper;
import com.aischool.server.mapper.ShopItemMapper;
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.service.auth.PermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端：成长银行（仅管理员）——商品 CRUD/启停 + 兑换记录；操行分规则见 AdminIndicatorController。
 */
@RestController
@RequestMapping("/api/admin/shop")
@RequiredArgsConstructor
public class AdminShopController {

    private final ShopItemMapper shopItemMapper;
    private final CoinExpenseMapper coinExpenseMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final PermissionService permissionService;

    private void checkAdmin() {
        permissionService.checkAdminAccess("只有管理员可操作系统管理");
    }

    /** 商品列表（含下架，分页） */
    @GetMapping("/item/list")
    public ApiResponse<Map<String, Object>> itemList(@RequestParam(required = false) Integer status,
                                                     @RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "20") long size) {
        checkAdmin();
        var p = shopItemMapper.selectPage(Page.of(page, Math.min(size, 100)), new LambdaQueryWrapper<ShopItem>()
                .eq(status != null, ShopItem::getStatus, status)
                .orderByAsc(ShopItem::getSort)
                .orderByAsc(ShopItem::getId));
        return ApiResponse.ok(pageOf(p.getRecords(), p.getTotal()));
    }

    @Data
    public static class ItemReq {
        @NotBlank(message = "name 不能为空")
        private String name;
        @NotNull(message = "priceCoin 不能为空")
        private BigDecimal priceCoin;
        private Integer stock;   // 空=-1 不限
        private Integer sort;
    }

    @PostMapping("/item")
    public ApiResponse<Map<String, Object>> createItem(@Validated @RequestBody ItemReq req) {
        checkAdmin();
        if (req.getPriceCoin().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "兑换价必须大于 0");
        }
        ShopItem item = new ShopItem();
        copy(req, item);
        item.setStatus(1);
        shopItemMapper.insert(item);
        return ApiResponse.ok(Map.of("itemId", item.getId()));
    }

    @PutMapping("/item/{id}")
    public ApiResponse<Void> updateItem(@PathVariable Long id, @Validated @RequestBody ItemReq req) {
        checkAdmin();
        ShopItem item = shopItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(404, "商品不存在");
        }
        if (req.getPriceCoin().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "兑换价必须大于 0");
        }
        copy(req, item);
        shopItemMapper.updateById(item);
        return ApiResponse.ok();
    }

    /** 上/下架（下架商品不出货架，历史流水不受影响） */
    @PutMapping("/item/{id}/status")
    public ApiResponse<Void> itemStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        checkAdmin();
        ShopItem item = shopItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(404, "商品不存在");
        }
        Integer status = body.get("status");
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(400, "status 只能为 0/1");
        }
        item.setStatus(status);
        shopItemMapper.updateById(item);
        return ApiResponse.ok();
    }

    @DeleteMapping("/item/{id}")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        checkAdmin();
        if (shopItemMapper.selectById(id) == null) {
            throw new BizException(404, "商品不存在");
        }
        shopItemMapper.deleteById(id);
        return ApiResponse.ok();
    }

    /** 兑换记录（联学生名/录入教师名） */
    @GetMapping("/expense/list")
    public ApiResponse<Map<String, Object>> expenseList(@RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "20") long size) {
        checkAdmin();
        var p = coinExpenseMapper.selectPage(Page.of(page, Math.min(size, 100)), new LambdaQueryWrapper<CoinExpense>()
                .orderByDesc(CoinExpense::getId));
        List<CoinExpense> rows = p.getRecords();
        Map<Long, String> studentNames = rows.isEmpty() ? Map.of()
                : studentMapper.selectBatchIds(rows.stream().map(CoinExpense::getStudentId).distinct().toList())
                        .stream().collect(Collectors.toMap(Student::getId, s ->
                                s.getName() == null ? "" : s.getName()));
        Map<Long, String> teacherNames = rows.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(rows.stream().map(CoinExpense::getOperatorId)
                        .filter(t -> t != null).distinct().toList())
                        .stream().collect(Collectors.toMap(User::getId, User::getRealName));
        List<Map<String, Object>> records = rows.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("studentId", e.getStudentId());
            m.put("studentName", studentNames.getOrDefault(e.getStudentId(), "(已删除)"));
            m.put("item", e.getItem());
            m.put("coin", e.getCoin());
            m.put("operatorName", e.getOperatorId() == null ? null
                    : teacherNames.getOrDefault(e.getOperatorId(), "(已删除)"));
            m.put("createTime", e.getCreateTime());
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", p.getTotal());
        return ApiResponse.ok(data);
    }

    private void copy(ItemReq req, ShopItem item) {
        item.setName(req.getName().trim());
        item.setPriceCoin(req.getPriceCoin());
        item.setStock(req.getStock() == null ? -1 : req.getStock());
        item.setSort(req.getSort() == null ? 0 : req.getSort());
    }

    private Map<String, Object> pageOf(List<ShopItem> records, long total) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        return data;
    }
}
