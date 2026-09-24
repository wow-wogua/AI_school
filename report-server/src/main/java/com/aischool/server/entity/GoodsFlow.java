package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** t_goods_flow 物资出入库流水（批9）：OUT 行=「谁(applicant)/什么时候(create_time)/在哪里(location)/拿走了什么(goods,qty)」 */
@Data
@TableName("t_goods_flow")
public class GoodsFlow {

    public static final String IN = "IN";
    public static final String OUT = "OUT";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long goodsId;
    private String goodsName;
    private Integer qty;
    private String direction;
    private String location;
    private Long formId;
    private Long applicantId;
    private Long operatorId;
    private String note;
    private LocalDateTime createTime;
}
