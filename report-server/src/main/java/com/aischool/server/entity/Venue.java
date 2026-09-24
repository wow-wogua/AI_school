package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** t_venue 场地字典（批11，管理端 CRUD） */
@Data
@TableName("t_venue")
public class Venue {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String location;
    private Integer capacity;
    private Integer status;
    private LocalDateTime createTime;
}
