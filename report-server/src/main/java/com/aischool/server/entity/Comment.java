package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_comment */
@Data
@TableName("t_comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long termId;
    private String type;
    private String content;
    private String aiDraft;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
