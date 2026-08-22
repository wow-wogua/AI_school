package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/** t_process_stat */
@Data
@TableName("t_process_stat")
public class ProcessStat {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long termId;
    private BigDecimal posMine;
    private BigDecimal posClassAvg;
    private BigDecimal posGradeAvg;
    private BigDecimal negMine;
    private BigDecimal negClassAvg;
    private BigDecimal negGradeAvg;
}
