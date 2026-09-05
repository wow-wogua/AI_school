package com.aischool.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aischool.server.entity.GridStatTerm;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

public interface GridStatTermMapper extends BaseMapper<GridStatTerm> {

    /** 原子 upsert（V6 唯一键保证 ODKU 生效）：并发写同一 (学生,学期,格) 不再丢增量 */
    @Insert("INSERT INTO t_grid_stat_term (student_id, term_id, grid_id, points, eval_count, kind_count, score) " +
            "VALUES (#{studentId}, #{termId}, #{gridId}, #{score}, 1, #{kindCount}, #{score}) " +
            "ON DUPLICATE KEY UPDATE points = points + #{score}, eval_count = eval_count + 1, " +
            "score = score + #{score}, kind_count = #{kindCount}")
    int upsertIncrement(@Param("studentId") Long studentId, @Param("termId") Long termId,
                        @Param("gridId") Long gridId, @Param("score") BigDecimal score,
                        @Param("kindCount") int kindCount);
}
