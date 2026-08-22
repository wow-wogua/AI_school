package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_activity */
@Data
@TableName("t_activity")
public class Activity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String type;
    private LocalDateTime startTime;
    private String place;
    private String coverUrl;
    private String intro;
    private Long creatorId;
}
