package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_report_template */
@Data
@TableName("t_report_template")
public class ReportTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String schoolName;
    private String sections;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
