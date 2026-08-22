package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/** t_coin_rate */
@Data
@TableName("t_coin_rate")
public class CoinRate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private BigDecimal rate;
    private LocalDate effectiveDate;
}
