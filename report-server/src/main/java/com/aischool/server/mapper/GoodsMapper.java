package com.aischool.server.mapper;

import com.aischool.server.entity.Goods;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {

    /** 原子扣减：库存不足返回 0（申领通过时防超发） */
    @Update("UPDATE t_goods SET stock = stock - #{qty} WHERE id = #{id} AND stock >= #{qty}")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);
}
