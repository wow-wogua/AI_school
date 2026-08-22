package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** t_coin_income */
@Data
@TableName("t_coin_income")
public class CoinIncome {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long termId;
    private String sourceType;
    private Long sourceId;
    private String module;
    private BigDecimal score;
    private BigDecimal coin;
    private Integer displayOrder;
    private LocalDateTime createTime;
}
