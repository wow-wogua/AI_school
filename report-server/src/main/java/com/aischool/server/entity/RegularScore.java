package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/** t_regular_score */
@Data
@TableName("t_regular_score")
public class RegularScore {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long subjectId;
    private Long termId;
    private BigDecimal score;
    private LocalDate date;
}
