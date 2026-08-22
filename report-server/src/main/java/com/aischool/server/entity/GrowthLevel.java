package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/** t_growth_level */
@Data
@TableName("t_growth_level")
public class GrowthLevel {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer level;
    private BigDecimal minScore;
    private String symbolName;
    private String symbolImg;
}
