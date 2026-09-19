package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_parent_binding */
@Data
@TableName("t_parent_binding")
public class ParentBinding {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentUserId;
    private Long studentId;
    private String relation;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
