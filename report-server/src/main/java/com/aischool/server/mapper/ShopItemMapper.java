package com.aischool.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aischool.server.entity.ShopItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ShopItemMapper extends BaseMapper<ShopItem> {

    /** 原子扣库存（不限量 stock=-1 时不调用） */
    @Update("UPDATE t_shop_item SET stock = stock - 1 WHERE id = #{id} AND stock > 0")
    int deductStock(@Param("id") Long id);
}
