package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** t_evaluation */
@Data
@TableName("t_evaluation")
public class Evaluation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long teacherId;
    private Long indicatorId;
    private String title;
    private BigDecimal score;
    private String remark;
    private LocalDateTime evalTime;
}
