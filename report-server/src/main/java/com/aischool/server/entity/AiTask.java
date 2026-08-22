package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** t_ai_task：AI 分析任务队列（排队/生成中/成功/失败） */
@Data
@TableName("t_ai_task")
public class AiTask {

    public static final String COMMENT = "COMMENT";
    public static final String SUMMARY = "SUMMARY";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskType;
    private Long studentId;
    private Long termId;
    private String status;
    private String source;
    private String resultJson;
    private String error;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
}
