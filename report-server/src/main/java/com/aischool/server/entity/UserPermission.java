package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_user_permission */
@Data
@TableName("t_user_permission")
public class UserPermission {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String permCode;
    private Long grantedBy;
    private LocalDateTime createTime;
}
