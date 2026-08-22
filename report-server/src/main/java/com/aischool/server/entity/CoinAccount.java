package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** t_coin_account */
@Data
@TableName("t_coin_account")
public class CoinAccount {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private BigDecimal currentCoin;
    private BigDecimal totalCoin;
    private LocalDateTime updateTime;
}
