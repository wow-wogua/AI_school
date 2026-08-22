package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/** t_subject_stat_week */
@Data
@TableName("t_subject_stat_week")
public class SubjectStatWeek {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long termId;
    private Long subjectId;
    private Integer weekNo;
    private BigDecimal mine;
    private BigDecimal classAvg;
    private BigDecimal gradeAvg;
}
