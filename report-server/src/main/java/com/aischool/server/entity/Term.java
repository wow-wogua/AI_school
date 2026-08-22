package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;

/** t_term */
@Data
@TableName("t_term")
public class Term {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer isCurrent;
}
