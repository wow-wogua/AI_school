package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/** t_score */
@Data
@TableName("t_score")
public class Score {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long examId;
    private Long subjectId;
    private Long studentId;
    private BigDecimal score;
    private Integer classRank;
    private Integer gradeRank;
    private Long createdBy;
}
