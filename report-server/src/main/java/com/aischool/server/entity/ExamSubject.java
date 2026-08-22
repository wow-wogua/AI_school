package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/** t_exam_subject */
@Data
@TableName("t_exam_subject")
public class ExamSubject {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long examId;
    private Long subjectId;
    private BigDecimal fullScore;
    private BigDecimal classMax;
    private BigDecimal gradeMax;
}
