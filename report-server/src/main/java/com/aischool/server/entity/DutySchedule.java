package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** t_duty_schedule（日常评价值班排班，批6 漏项C1） */
@Data
@TableName("t_duty_schedule")
public class DutySchedule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate dutyDate;
    private Long teacherId;
    private String note;
    private LocalDateTime createTime;
}
