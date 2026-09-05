package com.aischool.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aischool.server.entity.GridStatWeek;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

public interface GridStatWeekMapper extends BaseMapper<GridStatWeek> {

    /** 原子 upsert（V6 唯一键保证 ODKU 生效）：并发写同一 (学生,学期,格,周) 不再丢增量 */
    @Insert("INSERT INTO t_grid_stat_week (student_id, term_id, grid_id, week_no, score) " +
            "VALUES (#{studentId}, #{termId}, #{gridId}, #{weekNo}, #{score}) " +
            "ON DUPLICATE KEY UPDATE score = score + #{score}")
    int upsertIncrement(@Param("studentId") Long studentId, @Param("termId") Long termId,
                        @Param("gridId") Long gridId, @Param("weekNo") int weekNo,
                        @Param("score") BigDecimal score);
}
