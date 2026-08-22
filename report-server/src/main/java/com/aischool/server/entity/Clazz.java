package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_class */
@Data
@TableName("t_class")
public class Clazz {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long gradeId;
    private String name;
    private Long headTeacherId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
