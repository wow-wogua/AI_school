package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** t_oa_form OA 审批单（批9：公章/物资） */
@Data
@TableName("t_oa_form")
public class OaForm {

    public static final String TYPE_SEAL = "SEAL";
    public static final String TYPE_GOODS = "GOODS";

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String REVOKED = "REVOKED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String formType;
    private String title;
    /** 类型专属明细 JSON（SEAL:{reason,useDate} GOODS:[{goodsId,name,qty,unit,location}]） */
    private String detail;
    private Long applicantId;
    private String status;
    private Integer currentLevel;
    private LocalDateTime finishTime;
    private LocalDateTime createTime;
}
