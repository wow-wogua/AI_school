package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/** t_class_grid_avg */
@Data
@TableName("t_class_grid_avg")
public class ClassGridAvg {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long classId;
    private Long termId;
    private Long gridId;
    private BigDecimal avgScore;
}
