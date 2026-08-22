package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** t_honor */
@Data
@TableName("t_honor")
public class Honor {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private String name;
    private String level;
    private String issuer;
    private LocalDate honorDate;
    private String fileUrl;
    private String aiParsed;
    private String confirmStatus;
    private LocalDateTime createTime;
}
