package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** t_class_score 班级整体加减分（批43）：一行=某班某日一条整体加/减分事件，不落具体学生 */
@Data
@TableName("t_class_score")
public class ClassScore {

    /** 单条分值上限（对齐旧文明班打分口径 ±50） */
    public static final int DELTA_LIMIT = 50;
    /** 记分日期窗口（对齐旧文明班打分：当日往前 31 天） */
    public static final int DATE_WINDOW_DAYS = 31;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long classId;
    private LocalDate scoreDate;
    private Integer category;
    private String itemText;
    private BigDecimal delta;
    private String note;
    private Long operatorId;
    private LocalDateTime createTime;
}
