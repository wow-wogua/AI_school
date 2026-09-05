package com.aischool.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aischool.server.entity.ClassGridAvg;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

public interface ClassGridAvgMapper extends BaseMapper<ClassGridAvg> {

    /** 均值增量平移（原子 upsert）：delta=ROUND(s/班规模,4) 在 Java 算好传入，落库列 DECIMAL(10,2) 取整，与原口径逐位一致 */
    @Insert("INSERT INTO t_class_grid_avg (class_id, term_id, grid_id, avg_score) " +
            "VALUES (#{classId}, #{termId}, #{gridId}, #{delta}) " +
            "ON DUPLICATE KEY UPDATE avg_score = avg_score + #{delta}")
    int upsertShift(@Param("classId") Long classId, @Param("termId") Long termId,
                    @Param("gridId") Long gridId, @Param("delta") BigDecimal delta);
}
