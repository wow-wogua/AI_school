package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 成长银行商品（t_shop_item，批3） */
@Data
@TableName("t_shop_item")
public class ShopItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private BigDecimal priceCoin;
    private Integer stock;      // -1=不限
    private Integer sort;
    private Integer status;     // 1 上架 / 0 下架
    private LocalDateTime createTime;
}
