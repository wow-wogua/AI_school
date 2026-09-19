package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理员/领导账号双人审批（t_role_request，批2-5） */
@Data
@TableName("t_role_request")
public class RoleRequest {
    public static final String TYPE_CREATE = "CREATE";
    public static final String TYPE_UPGRADE = "UPGRADE";
    public static final String ST_PENDING = "PENDING";
    public static final String ST_APPROVED = "APPROVED";
    public static final String ST_REJECTED = "REJECTED";

    @com.baomidou.mybatisplus.annotation.TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String reqType;
    private String targetRole;
    private String fromRole;
    private Long requestedBy;
    private String status;
    private Long handledBy;
    private LocalDateTime handleTime;
    private String handleNote;
    private LocalDateTime createTime;
}
