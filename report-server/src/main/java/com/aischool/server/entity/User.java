package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_user */
@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String realName;
    private String role;
    private String phone;
    private String stageScope; // 批39③：领导分管学段 PRIMARY/JUNIOR，null=全部（仅 LEADER 使用）
    private Integer status;
    private Integer mustChangePwd;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
