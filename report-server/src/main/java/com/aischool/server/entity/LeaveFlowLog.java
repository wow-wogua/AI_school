package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** t_leave_flow_log 请假流转日志（批32）：详情页审批/登记时间线数据源 */
@Data
@TableName("t_leave_flow_log")
public class LeaveFlowLog {

    public static final String SUBMIT = "SUBMIT";
    public static final String APPROVE = "APPROVE";
    public static final String REJECT = "REJECT";
    public static final String CANCEL = "CANCEL";
    public static final String REG_LEAVE = "LEAVE";
    public static final String REG_RETURN = "RETURN";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long leaveId;
    private String action;
    private Integer step;
    private Long operatorId;
    private String note;
    private LocalDateTime createTime;
}
