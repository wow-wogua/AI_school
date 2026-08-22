package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/** t_homework_stat */
@Data
@TableName("t_homework_stat")
public class HomeworkStat {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long termId;
    private Long subjectId;
    private Integer colType;
    private BigDecimal score;
    private Integer times;
}
