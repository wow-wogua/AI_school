package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** t_teacher_footprint 教师成长足迹（公开课/听课/讲座/读书笔记/工作室；奖项聚合 t_teacher_honor） */
@Data
@TableName("t_teacher_footprint")
public class TeacherFootprint {

    public static final String T_OPEN_CLASS = "OPEN_CLASS";
    public static final String T_OBSERVE = "OBSERVE";
    public static final String T_LECTURE = "LECTURE";
    public static final String T_READING = "READING";
    public static final String T_STUDIO = "STUDIO";
    public static final java.util.List<String> TYPES =
            java.util.List.of(T_OPEN_CLASS, T_OBSERVE, T_LECTURE, T_READING, T_STUDIO);

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teacherId;
    private String type;
    private String title;
    private LocalDate footDate;
    private String place;
    private String note;
    private LocalDateTime createTime;
}
