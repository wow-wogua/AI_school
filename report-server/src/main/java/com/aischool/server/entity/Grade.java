package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_grade */
@Data
@TableName("t_grade")
public class Grade {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String schoolYear;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
