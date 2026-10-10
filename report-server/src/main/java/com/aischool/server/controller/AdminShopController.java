package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.CoinExpense;
import com.aischool.server.entity.CoinIncome;
import com.aischool.server.entity.ShopItem;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.CoinExpenseMapper;
import com.aischool.server.mapper.CoinIncomeMapper;
import com.aischool.server.mapper.ShopItemMapper;
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.service.auth.PermissionService;
import com.aischool.server.service.coin.CoinLedgerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 管理端：成长银行（仅管理员）——商品 CRUD/启停 + 兑换记录 + 班级获奖按班发币；操行分规则见 AdminIndicatorController。
 */
@RestController
@RequestMapping("/api/admin/shop")
@RequiredArgsConstructor
public class AdminShopController {

    private final ShopItemMapper shopItemMapper;
    private final CoinExpenseMapper coinExpenseMapper;
    private final CoinIncomeMapper coinIncomeMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ClazzMapper clazzMapper;
    private final CoinLedgerService coinLedger;
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
        // operatorId 可空（批量导入的旧记录）：过滤后可能为空列表，selectBatchIds(空) 生成 IN () 非法 SQL
        List<Long> teacherIds = rows.stream().map(CoinExpense::getOperatorId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> teacherNames = teacherIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(teacherIds)
                        .stream().collect(Collectors.toMap(User::getId, User::getRealName));
        List<Map<String, Object>> records = rows.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("studentId", e.getStudentId());
            m.put("studentName", studentNames.getOrDefault(e.getStudentId(), "(已删除)"));
            m.put("item", e.getItem());
            m.put("coin", e.getCoin());
            m.put("status", e.getStatus() == null ? 1 : e.getStatus()); // 旧记录无值=已领取
            m.put("confirmTime", e.getConfirmTime());
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

    // ───────────────── 批48：班级获奖按班发币（成长银行方案：双优班级8/优秀班5/达标班2 每生）─────────────────

    /** 兑换方案固定档位（前端快捷按钮；后端只校验币值范围，称号文本自由） */
    public static final Map<String, Integer> GRANT_TIERS = Map.of(
            "双优班级", 8, "优秀文明班", 5, "学习习惯示范班", 5, "学习先进班", 5, "学习标兵班", 5, "达标文明班", 2);

    @Data
    public static class GrantReq {
        @NotNull(message = "classId 不能为空")
        private Long classId;
        @NotBlank(message = "称号不能为空")
        private String title;
        @NotNull(message = "coin 不能为空")
        private BigDecimal coin;
    }

    /** 按班发币：全班在读学生每生同额入账；batchId（毫秒时间戳）作 sourceId 供整批冲正。同班同日同称号防重发。 */
    @PostMapping("/coin/grant")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> grant(@Validated @RequestBody GrantReq req) {
        checkAdmin();
        String title = req.getTitle().trim();
        if (title.length() > 20) {
            throw new BizException(400, "称号不能超过 20 字");
        }
        BigDecimal coin = req.getCoin();
        if (coin.compareTo(BigDecimal.ONE) < 0 || coin.compareTo(BigDecimal.valueOf(50)) > 0) {
            throw new BizException(400, "每生币数须在 1~50 之间");
        }
        Clazz clazz = clazzMapper.selectById(req.getClassId());
        if (clazz == null) {
            throw new BizException(404, "班级不存在");
        }
        List<Student> students = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, req.getClassId())
                .eq(Student::getStatus, "在读"));
        if (students.isEmpty()) {
            throw new BizException(400, "该班级没有在读学生");
        }
        // 防重：今日同称号流水 ∩ 本班学生（流水只存学生/称号，不含班级；不影响同日多班获同称号）
        List<Long> todaySids = coinIncomeMapper.selectList(new LambdaQueryWrapper<CoinIncome>()
                        .eq(CoinIncome::getSourceType, "班级获奖")
                        .eq(CoinIncome::getModule, title)
                        .ge(CoinIncome::getCreateTime, LocalDate.now().atStartOfDay()))
                .stream().map(CoinIncome::getStudentId).toList();
        if (students.stream().anyMatch(s -> todaySids.contains(s.getId()))) {
            throw new BizException(400, "该班今天已按「" + title + "」发过币，请勿重复操作");
        }
        long batchId = System.currentTimeMillis();
        for (Student s : students) {
            coinLedger.income(s.getId(), LocalDate.now(), "班级获奖", batchId, title, coin);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("batchId", batchId);
        m.put("count", students.size());
        m.put("coin", coin);
        m.put("title", title);
        return ApiResponse.ok(m);
    }

    /** 发币记录（按 batchId 聚合，含冲正状态；联班级名/操作班级首个学生反查） */
    @GetMapping("/coin/grant/list")
    public ApiResponse<List<Map<String, Object>>> grantList() {
        checkAdmin();
        List<CoinIncome> rows = coinIncomeMapper.selectList(new LambdaQueryWrapper<CoinIncome>()
                .eq(CoinIncome::getSourceType, "班级获奖")
                .orderByDesc(CoinIncome::getId)
                .last("LIMIT 2000"));
        // 同批负行=已冲正；按 batchId 聚合取最近 20 批
        Map<Long, List<CoinIncome>> byBatch = rows.stream()
                .filter(r -> r.getSourceId() != null)
                .collect(Collectors.groupingBy(CoinIncome::getSourceId, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> out = byBatch.values().stream().limit(20).map(batch -> {
            CoinIncome first = batch.get(0);
            boolean reversed = batch.stream().anyMatch(r -> r.getCoin().signum() < 0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("batchId", first.getSourceId());
            m.put("title", first.getModule().replaceFirst("^撤回-", ""));
            m.put("coin", first.getCoin().abs());
            m.put("count", batch.stream().filter(r -> r.getCoin().signum() > 0).count());
            m.put("createTime", first.getCreateTime());
            m.put("reversed", reversed);
            // 班级名：取该批任一学生反查
            Student s = batch.stream().filter(r -> r.getCoin().signum() > 0).findFirst()
                    .map(r -> studentMapper.selectById(r.getStudentId())).orElse(null);
            m.put("className", s != null && s.getClassId() != null
                    ? Optional.ofNullable(clazzMapper.selectById(s.getClassId())).map(Clazz::getName).orElse("(已删除)") : "(已删除)");
            return m;
        }).toList();
        return ApiResponse.ok(out);
    }

    /** 整批冲正（发错撤销）：对该批每生记负流水并扣回账户；重复冲正拒绝 */
    @PostMapping("/coin/grant/reverse")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> grantReverse(@RequestBody Map<String, Long> body) {
        checkAdmin();
        Long batchId = body.get("batchId");
        if (batchId == null) {
            throw new BizException(400, "batchId 不能为空");
        }
        List<CoinIncome> rows = coinIncomeMapper.selectList(new LambdaQueryWrapper<CoinIncome>()
                .eq(CoinIncome::getSourceType, "班级获奖")
                .eq(CoinIncome::getSourceId, batchId));
        if (rows.isEmpty()) {
            throw new BizException(404, "发币批次不存在");
        }
        if (rows.stream().anyMatch(r -> r.getCoin().signum() < 0)) {
            throw new BizException(400, "该批次已冲正过");
        }
        for (Long sid : rows.stream().map(CoinIncome::getStudentId).distinct().toList()) {
            coinLedger.reverse(sid, "班级获奖", batchId);
        }
        return ApiResponse.ok(Map.of("batchId", batchId, "reversed", rows.size()));
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
