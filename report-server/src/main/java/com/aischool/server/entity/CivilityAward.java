package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** t_civility_award 文明班评选快照（批30）：按月冻结各班排名 */
@Data
@TableName("t_civility_award")
public class CivilityAward {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String periodType;
    private String periodValue;
    private Long classId;
    private Long gradeId;
    private Integer rankNo;
    private BigDecimal totalScore;
    private LocalDateTime settleTime;
    private Long settleBy;
}
