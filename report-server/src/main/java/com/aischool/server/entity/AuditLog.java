package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** t_audit_log：写操作审计（请求级：谁/何时/对哪个接口/参数摘要/结果码） */
@Data
@TableName("t_audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String method;
    private String uri;
    private String body;
    private Integer status;
    private String ip;
    private LocalDateTime createTime;
}
