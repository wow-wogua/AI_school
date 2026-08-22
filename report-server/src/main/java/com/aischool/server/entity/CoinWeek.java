package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/** t_coin_week */
@Data
@TableName("t_coin_week")
public class CoinWeek {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long termId;
    private Integer weekNo;
    private BigDecimal inMine;
    private BigDecimal inClass;
    private BigDecimal inGrade;
    private BigDecimal outMine;
    private BigDecimal outClass;
    private BigDecimal outGrade;
}
