package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_report */
@Data
@TableName("t_report")
public class Report {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long studentId;
    private Long termId;
    private String fileUrl;
    private Integer pageCount;
    private LocalDateTime genTime;
    private String status;
    private String error;
    private LocalDateTime createTime;
}
