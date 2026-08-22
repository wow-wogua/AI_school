package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/** t_subject_stat_term */
@Data
@TableName("t_subject_stat_term")
public class SubjectStatTerm {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long termId;
    private Long subjectId;
    private BigDecimal posMine;
    private BigDecimal posClassAvg;
    private BigDecimal posGradeAvg;
    private BigDecimal negMine;
    private BigDecimal negClassAvg;
    private BigDecimal negGradeAvg;
}
