package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 操行分规则（t_conduct_rule，全校一套单行，批3） */
@Data
@TableName("t_conduct_rule")
public class ConductRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private BigDecimal baseScore;
    private BigDecimal gradeAMin;
    private BigDecimal gradeBMin;
    private BigDecimal gradeCMin;
    private LocalDateTime updateTime;
}
