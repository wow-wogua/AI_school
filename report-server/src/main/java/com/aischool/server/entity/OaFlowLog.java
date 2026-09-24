package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** t_oa_flow_log OA 流转日志（批9） */
@Data
@TableName("t_oa_flow_log")
public class OaFlowLog {

    public static final String SUBMIT = "SUBMIT";
    public static final String AGREE = "AGREE";
    public static final String REJECT = "REJECT";
    public static final String REVOKE = "REVOKE";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long formId;
    private String action;
    private Integer level;
    private String nodeName;
    private Long operatorId;
    private String note;
    private LocalDateTime createTime;
}
