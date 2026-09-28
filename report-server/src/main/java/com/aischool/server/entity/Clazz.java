package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_class */
@Data
@TableName("t_class")
public class Clazz {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long gradeId;
    private String name;
    private Long headTeacherId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** 批28 文件归档：该班文件包最近一次导出时间（导出过才允许清理学生照片） */
    private LocalDateTime archivedTime;
}
