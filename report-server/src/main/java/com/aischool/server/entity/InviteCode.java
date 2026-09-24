package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** t_invite_code 家长绑定邀请码（批8.6，一码一学生） */
@Data
@TableName("t_invite_code")
public class InviteCode {

    public static final int UNUSED = 0;
    public static final int USED = 1;
    public static final int VOID = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private String code;
    private Integer status;
    private Long usedBy;
    private LocalDateTime createTime;
}
