package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_report_task */
@Data
@TableName("t_report_task")
public class ReportTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long termId;
    private String scope;
    private Long targetId;
    private String status;
    private Integer total;
    private Integer done;
    private Integer failed;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
