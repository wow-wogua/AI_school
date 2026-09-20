package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_moment_tag（微光场景标签字典，批6 漏项H） */
@Data
@TableName("t_moment_tag")
public class MomentTag {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer sort;
    private Long createUserId;
    private LocalDateTime createTime;
}
