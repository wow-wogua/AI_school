package com.aischool.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aischool.server.entity.GradeGridAvg;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

public interface GradeGridAvgMapper extends BaseMapper<GradeGridAvg> {

    /** 均值增量平移（原子 upsert）：delta=ROUND(s/年级规模,4) 在 Java 算好传入，落库列 DECIMAL(10,2) 取整，与原口径逐位一致 */
    @Insert("INSERT INTO t_grade_grid_avg (grade_id, term_id, grid_id, avg_score) " +
            "VALUES (#{gradeId}, #{termId}, #{gridId}, #{delta}) " +
            "ON DUPLICATE KEY UPDATE avg_score = avg_score + #{delta}")
    int upsertShift(@Param("gradeId") Long gradeId, @Param("termId") Long termId,
                    @Param("gridId") Long gridId, @Param("delta") BigDecimal delta);
}
