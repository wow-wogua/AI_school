package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.CoinAccount;
import com.aischool.server.entity.CoinExpense;
import com.aischool.server.entity.ShopItem;
import com.aischool.server.mapper.CoinAccountMapper;
import com.aischool.server.mapper.CoinExpenseMapper;
import com.aischool.server.mapper.ShopItemMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.auth.DataScopeService;
import com.aischool.server.service.conduct.ConductLedgerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成长银行（批3）：教师 App 录入兑换——选学生+商品，验余额原子扣账。
 */
@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopItemMapper shopItemMapper;
    private final CoinAccountMapper coinAccountMapper;
    private final CoinExpenseMapper coinExpenseMapper;
    private final ConductLedgerService conductLedger;
    private final DataScopeService dataScope;

    /** 货架（上架商品，按 sort）；兑换录入=教师操作，家长白名单外一律 403 */
    @GetMapping("/items")
    public ApiResponse<List<ShopItem>> items() {
        rejectParent();
        return ApiResponse.ok(shopItemMapper.selectList(new LambdaQueryWrapper<ShopItem>()
                .eq(ShopItem::getStatus, 1)
                .orderByAsc(ShopItem::getSort)
                .orderByAsc(ShopItem::getId)));
    }

    private void rejectParent() {
        if ("PARENT".equals(AuthUtil.current().role())) {
            throw new BizException(403, "仅教师可访问");
        }
    }

    /** 学生能量币余额 + 最近兑换记录（兑换前核账） */
    @GetMapping("/account/{studentId}")
    public ApiResponse<Map<String, Object>> account(@PathVariable Long studentId) {
        dataScope.checkStudentAccess(AuthUtil.current(), studentId);
        CoinAccount account = coinAccountMapper.selectOne(new LambdaQueryWrapper<CoinAccount>()
                .eq(CoinAccount::getStudentId, studentId).last("LIMIT 1"));
        List<CoinExpense> expenses = coinExpenseMapper.selectList(new LambdaQueryWrapper<CoinExpense>()
                .eq(CoinExpense::getStudentId, studentId)
                .orderByDesc(CoinExpense::getId)
                .last("LIMIT 5"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("currentCoin", account != null ? account.getCurrentCoin() : BigDecimal.ZERO);
        m.put("totalCoin", account != null ? account.getTotalCoin() : BigDecimal.ZERO);
        m.put("recentExpenses", expenses);
        return ApiResponse.ok(m);
    }

    /** 录入兑换：原子扣余额（不足拒扣）→ 记流水（商品名快照）→ 限量商品原子扣库存 */
    @PostMapping("/redeem")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> redeem(@Validated @RequestBody RedeemReq req) {
        dataScope.checkStudentAccess(AuthUtil.current(), req.getStudentId());
        ShopItem item = shopItemMapper.selectById(req.getItemId());
        if (item == null || item.getStatus() == null || item.getStatus() != 1) {
            throw new BizException(404, "商品不存在或已下架");
        }
        // 兑换频控（原始需求「一月一次，兑换学校文创」）：自然月内已有任一商品兑换记录即拒
        Long monthRedeemed = coinExpenseMapper.selectCount(new LambdaQueryWrapper<CoinExpense>()
                .eq(CoinExpense::getStudentId, req.getStudentId())
                .isNotNull(CoinExpense::getItemId)
                .ge(CoinExpense::getCreateTime, LocalDate.now().withDayOfMonth(1).atStartOfDay()));
        if (monthRedeemed != null && monthRedeemed > 0) {
            throw new BizException(400, "该学生本月已兑换过（每人每月限兑一次），下月 1 日再来");
        }
        if (item.getStock() != null && item.getStock() >= 0) {
            if (item.getStock() <= 0 || shopItemMapper.deductStock(item.getId()) == 0) {
                throw new BizException(400, "商品库存不足");
            }
        }
        if (coinAccountMapper.upsertExpense(req.getStudentId(), item.getPriceCoin()) == 0) {
            throw new BizException(400, "能量币余额不足");
        }
        CoinExpense row = new CoinExpense();
        row.setStudentId(req.getStudentId());
        row.setTermId(conductLedger.resolveTermId(LocalDate.now()));
        row.setItem(item.getName());           // 商品名快照，改名不影响历史记录
        row.setCoin(item.getPriceCoin());
        row.setItemId(item.getId());
        row.setOperatorId(AuthUtil.current().userId());
        row.setCreateTime(LocalDateTime.now()); // 该列无默认值，见 t_coin_income 同款处理
        coinExpenseMapper.insert(row);

        CoinAccount after = coinAccountMapper.selectOne(new LambdaQueryWrapper<CoinAccount>()
                .eq(CoinAccount::getStudentId, req.getStudentId()).last("LIMIT 1"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expenseId", row.getId());
        m.put("itemName", item.getName());
        m.put("coin", item.getPriceCoin());
        m.put("currentCoin", after != null ? after.getCurrentCoin() : BigDecimal.ZERO);
        return ApiResponse.ok(m);
    }

    @Data
    public static class RedeemReq {
        @NotNull(message = "studentId 不能为空")
        private Long studentId;
        @NotNull(message = "itemId 不能为空")
        private Long itemId;
    }
}
