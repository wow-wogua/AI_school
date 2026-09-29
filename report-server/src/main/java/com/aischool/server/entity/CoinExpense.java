package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** t_coin_expense */
@Data
@TableName("t_coin_expense")
public class CoinExpense {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long termId;
    private String item;
    private BigDecimal coin;
    private Long itemId;      // 关联 t_shop_item（手动录入为 NULL，批3）
    private Long operatorId;  // 兑换录入教师（批3）
    private Integer status;   // 0=待领取 1=已领取（核销，批30）
    private LocalDateTime confirmTime; // 核销时间（批30）
    private Long confirmBy;   // 核销教师（批30）
    private LocalDateTime createTime;
}
