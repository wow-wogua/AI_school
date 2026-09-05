package com.aischool.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aischool.server.entity.CoinWeek;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

public interface CoinWeekMapper extends BaseMapper<CoinWeek> {

    /** 原子 upsert（V6 唯一键保证 ODKU 生效）；只动 in_mine——in_class/in_grade 是全组共现值，改动会波及他人报告 */
    @Insert("INSERT INTO t_coin_week (student_id, term_id, week_no, in_mine, in_class, in_grade, out_mine, out_class, out_grade) " +
            "VALUES (#{studentId}, #{termId}, #{weekNo}, #{score}, 0, 0, 0, 0, 0) " +
            "ON DUPLICATE KEY UPDATE in_mine = in_mine + #{score}")
    int upsertMineIncome(@Param("studentId") Long studentId, @Param("termId") Long termId,
                         @Param("weekNo") int weekNo, @Param("score") BigDecimal score);
}
