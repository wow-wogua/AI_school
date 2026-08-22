package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** t_comprehensive */
@Data
@TableName("t_comprehensive")
public class Comprehensive {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long termId;
    private String moral;
    private String ability;
    private String health;
    private String aesthetic;
    private String practice;
    private String finalLevel;
}
