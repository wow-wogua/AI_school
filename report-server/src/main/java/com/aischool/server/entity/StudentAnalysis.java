package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** t_student_analysis */
@Data
@TableName("t_student_analysis")
public class StudentAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long termId;
    private String advantage;
    private String toImprove;
    private String radarAdvantages;
    private String radarToImprove;
}
