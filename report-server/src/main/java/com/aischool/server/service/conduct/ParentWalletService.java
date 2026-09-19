package com.aischool.server.service.conduct;

import com.aischool.server.entity.CoinAccount;
import com.aischool.server.entity.CoinExpense;
import com.aischool.server.entity.ConductAccount;
import com.aischool.server.entity.ConductRule;
import com.aischool.server.mapper.CoinAccountMapper;
import com.aischool.server.mapper.CoinExpenseMapper;
import com.aischool.server.mapper.ConductAccountMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** 家长端孩子双账本组装（批3）：操行分 + 能量币，只读 */
@Service
@RequiredArgsConstructor
public class ParentWalletService {

    private final ConductLedgerService conductLedger;
    private final ConductAccountMapper conductAccountMapper;
    private final CoinAccountMapper coinAccountMapper;
    private final CoinExpenseMapper coinExpenseMapper;

    public Map<String, Object> walletOf(Long studentId) {
        Long termId = conductLedger.resolveTermId(LocalDate.now());

        ConductRule rule = conductLedger.getRule();
        ConductAccount conduct = conductAccountMapper.selectOne(new LambdaQueryWrapper<ConductAccount>()
                .eq(ConductAccount::getStudentId, studentId)
                .eq(ConductAccount::getTermId, termId)
                .last("LIMIT 1"));
        BigDecimal conductBalance = conduct != null ? conduct.getBalance() : rule.getBaseScore();

        CoinAccount coin = coinAccountMapper.selectOne(new LambdaQueryWrapper<CoinAccount>()
                .eq(CoinAccount::getStudentId, studentId).last("LIMIT 1"));

        Map<String, Object> conductM = new LinkedHashMap<>();
        conductM.put("balance", conductBalance);
        conductM.put("grade", conductLedger.gradeOf(conductBalance, rule));
        conductM.put("rule", Map.of(
                "baseScore", rule.getBaseScore(),
                "gradeAMin", rule.getGradeAMin(),
                "gradeBMin", rule.getGradeBMin(),
                "gradeCMin", rule.getGradeCMin()));
        conductM.put("logs", conductLedger.logsOf(studentId, termId).stream().limit(5).toList());

        Map<String, Object> coinM = new LinkedHashMap<>();
        coinM.put("currentCoin", coin != null ? coin.getCurrentCoin() : BigDecimal.ZERO);
        coinM.put("totalCoin", coin != null ? coin.getTotalCoin() : BigDecimal.ZERO);
        coinM.put("expenses", coinExpenseMapper.selectList(new LambdaQueryWrapper<CoinExpense>()
                .eq(CoinExpense::getStudentId, studentId)
                .orderByDesc(CoinExpense::getId)
                .last("LIMIT 5")));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("conduct", conductM);
        m.put("coin", coinM);
        return m;
    }
}