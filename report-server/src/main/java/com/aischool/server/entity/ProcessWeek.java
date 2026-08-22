package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/** t_process_week */
@Data
@TableName("t_process_week")
public class ProcessWeek {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long termId;
    private Integer weekNo;
    private BigDecimal mine;
    private BigDecimal classAvg;
    private BigDecimal gradeAvg;
}
