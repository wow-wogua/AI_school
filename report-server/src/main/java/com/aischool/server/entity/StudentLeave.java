package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * t_student_leave 学生请假单。
 * 批27~31：家长替孩子提交，单级审批。
 * 批32：家长不再提交（微信/电话告知班主任，由教师代录 creator_id）；时间粒度到时分（startTime/endTime）；
 * 按时长分级审批（duration_days 对照 t_sys_config 阈值：total_step 0=录入即生效 / 1=级长 / 2=级长+学成中心主任）。
 * parentId 保留为历史字段（批32 之前家长提交的单据），新逻辑一律走 studentId 关联全部绑定家长。
 */
@Data
@TableName("t_student_leave")
public class StudentLeave {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    /** 请假类型白名单 */
    public static final java.util.Set<String> TYPES = java.util.Set.of("病假", "事假", "其他");

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    /** 历史字段（批32 前家长提交单）；教师代录单为 NULL */
    private Long parentId;
    /** 发起教师（批32）：班主任/生活老师/级长/学成中心主任/领导/管理员 */
    private Long creatorId;
    private String leaveType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String reason;
    /** 时长（天，自动折算到 0.1 天，分级审批依据） */
    private BigDecimal durationDays;
    /** 凭证照片 objectName JSON 数组（≤3，MinIO student_leave/ 前缀，如病假条） */
    private String photos;
    private String status;
    private Long approverId;
    private String approveNote;
    private LocalDateTime approveTime;
    /** 审批级数（0=录入即生效 / 1=级长一级 / 2=级长+学成中心主任） */
    private Integer totalStep;
    /** 当前待审级（1=待级长 / 2=待主任；终态=0） */
    private Integer currentStep;
    private LocalDateTime leaveTime;
    private Long leaveGuardId;
    private LocalDateTime returnTime;
    private Long returnGuardId;
    private LocalDateTime createTime;
}
