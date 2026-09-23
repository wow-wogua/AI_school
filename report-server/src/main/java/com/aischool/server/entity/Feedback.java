package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** t_feedback 意见反馈（批8.5） */
@Data
@TableName("t_feedback")
public class Feedback {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String role;
    private String content;
    private String contact;
    private Integer status;   // 0=待处理 1=已处理
    private String handleNote;
    private Long handlerId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
