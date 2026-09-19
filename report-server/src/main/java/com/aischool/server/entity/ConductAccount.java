package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 操行分余额（t_conduct_account，每生每学期一行，批3） */
@Data
@TableName("t_conduct_account")
public class ConductAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long termId;
    private BigDecimal balance;
    private LocalDateTime updateTime;
}
