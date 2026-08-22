package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/** t_grid_stat_week */
@Data
@TableName("t_grid_stat_week")
public class GridStatWeek {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long termId;
    private Long gridId;
    private Integer weekNo;
    private BigDecimal score;
}
