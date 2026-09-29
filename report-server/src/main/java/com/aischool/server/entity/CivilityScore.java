package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** t_civility_score 文明班打分流水（批30）：一行=某班某日一条扣分/加分事件 */
@Data
@TableName("t_civility_score")
public class CivilityScore {

    /** 每班每天基础分（校方口径：12 项每项 10 分，1-11 扣分+12 特殊加减） */
    public static final int DAILY_BASE = 120;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long classId;
    private LocalDate scoreDate;
    private Integer sectionNo;
    private String itemText;
    private BigDecimal delta;
    private Integer cnt;
    private String note;
    private Long operatorId;
    private LocalDateTime createTime;
}
