package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_activity_signup */
@Data
@TableName("t_activity_signup")
public class ActivitySignup {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long studentId;
    private LocalDateTime signupTime;
    private LocalDateTime checkinTime;
    private String award;
    private String performance;
    private String evalText;
}
