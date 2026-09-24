package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** t_notice_read 通知已读回执（批9，uk(notice_id,user_id) 幂等打点） */
@Data
@TableName("t_notice_read")
public class NoticeRead {

    private Long noticeId;
    private Long userId;
    private LocalDateTime readTime;
}
