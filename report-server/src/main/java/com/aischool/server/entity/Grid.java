package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/** t_grid */
@Data
@TableName("t_grid")
public class Grid {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String icon;
    private Integer sort;
    private BigDecimal curAxisMax;
    private BigDecimal curAxisStep;
    private BigDecimal prevAxisMax;
    private BigDecimal prevAxisStep;
    private BigDecimal weekMin;
    private BigDecimal weekMax;
    private BigDecimal weekStep;
}
