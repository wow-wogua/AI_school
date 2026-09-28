package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** t_student_leave 学生请假单（批27）：家长替孩子提交，单级审批（任意一位教师批即生效），门卫登记离校/返校 */
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
    private Long parentId;
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    /** 凭证照片 objectName JSON 数组（≤3，MinIO student_leave/ 前缀，如病假条） */
    private String photos;
    private String status;
    private Long approverId;
    private String approveNote;
    private LocalDateTime approveTime;
    private LocalDateTime leaveTime;
    private Long leaveGuardId;
    private LocalDateTime returnTime;
    private Long returnGuardId;
    private LocalDateTime createTime;
}
