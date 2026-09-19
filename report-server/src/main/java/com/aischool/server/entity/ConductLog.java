package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 操行分流水（t_conduct_log，批3） */
@Data
@TableName("t_conduct_log")
public class ConductLog {
    public static final String SRC_EVALUATION = "评价";
    public static final String SRC_MANUAL = "手动";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long termId;
    private String sourceType;
    private Long sourceId;
    private BigDecimal delta;
    private String reason;
    private Long operatorId;
    private LocalDateTime createTime;
}
