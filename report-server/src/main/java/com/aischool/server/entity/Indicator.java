package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/** t_indicator */
@Data
@TableName("t_indicator")
public class Indicator {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long gridId;
    private String name;
    private String direction;
    private BigDecimal defaultScore;
    private String subjectScope;
}
