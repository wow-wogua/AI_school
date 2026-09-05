package com.aischool.server.service.coin;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.CoinIncome;
import com.aischool.server.entity.Term;
import com.aischool.server.mapper.CoinAccountMapper;
import com.aischool.server.mapper.CoinIncomeMapper;
import com.aischool.server.mapper.TermMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 能量币入账（活动/荣誉共用唯一入口）：写 t_coin_income 并同步 t_coin_account */
@Service
@RequiredArgsConstructor
public class CoinLedgerService {

    private final CoinIncomeMapper coinIncomeMapper;
    private final CoinAccountMapper coinAccountMapper;
    private final TermMapper termMapper;

    /**
     * 记收入并同步账户；termId 按日期落点推导（不在任何学期区间 → 当前学期）。
     *
     * @return 实际入账的 termId
     */
    public Long income(Long studentId, LocalDate date, String sourceType, Long sourceId,
                       String module, BigDecimal coin) {
        Term term = resolveTerm(date);
        CoinIncome row = new CoinIncome();
        row.setStudentId(studentId);
        row.setTermId(term.getId());
        row.setSourceType(sourceType);
        row.setSourceId(sourceId);
        row.setModule(module);
        row.setCoin(coin);
        row.setDisplayOrder(99);              // 评价种子模块为 1..8；99 不进收入 TOP5，自然落「收入最少模块」榜
        row.setCreateTime(LocalDateTime.now());  // 该列 NOT NULL 且无默认值
        coinIncomeMapper.insert(row);

        // 账户原子 upsert（student_id 建表即 UNIQUE）：并发入账不再丢增量
        coinAccountMapper.upsertIncome(studentId, coin);
        return term.getId();
    }

    private Term resolveTerm(LocalDate date) {
        if (date != null) {
            Term hit = termMapper.selectOne(new LambdaQueryWrapper<Term>()
                    .le(Term::getStartDate, date).ge(Term::getEndDate, date)
                    .last("LIMIT 1"));
            if (hit != null) {
                return hit;
            }
        }
        Term current = termMapper.selectOne(new LambdaQueryWrapper<Term>()
                .eq(Term::getIsCurrent, 1).last("LIMIT 1"));
        if (current == null) {
            throw new BizException(500, "无可用学期，能量币无法入账");
        }
        return current;
    }
}
