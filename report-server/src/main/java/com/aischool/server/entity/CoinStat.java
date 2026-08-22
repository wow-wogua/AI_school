package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** t_coin_stat */
@Data
@TableName("t_coin_stat")
public class CoinStat {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long termId;
    private String compareClassAvg;
    private String compareGradeAvg;
}
