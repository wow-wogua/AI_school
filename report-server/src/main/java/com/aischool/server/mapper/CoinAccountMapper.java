package com.aischool.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aischool.server.entity.CoinAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

public interface CoinAccountMapper extends BaseMapper<CoinAccount> {

    /** 原子 upsert（student_id 建表即 UNIQUE）：并发入账不再丢增量；update_time 走列默认值/ON UPDATE */
    @Insert("INSERT INTO t_coin_account (student_id, current_coin, total_coin) " +
            "VALUES (#{studentId}, #{coin}, #{coin}) " +
            "ON DUPLICATE KEY UPDATE current_coin = current_coin + #{coin}, total_coin = total_coin + #{coin}")
    int upsertIncome(@Param("studentId") Long studentId, @Param("coin") BigDecimal coin);
}
