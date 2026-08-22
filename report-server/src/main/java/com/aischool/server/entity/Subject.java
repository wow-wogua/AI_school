package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/** t_subject */
@Data
@TableName("t_subject")
public class Subject {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String shortName;
    private String type;
    private Integer sort;
    private Integer regularSort;
    private String motto;
    private BigDecimal procHMin;
    private BigDecimal procHMax;
    private BigDecimal procHStep;
    private BigDecimal procWMax;
}
