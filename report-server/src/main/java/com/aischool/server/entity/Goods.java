package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** t_goods 物资字典（批9，管理端 CRUD） */
@Data
@TableName("t_goods")
public class Goods {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String unit;
    private Integer stock;
    private String location;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
