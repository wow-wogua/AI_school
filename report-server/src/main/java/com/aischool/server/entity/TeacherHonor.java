package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** t_teacher_honor（教师成就·证书荣誉） */
@Data
@TableName("t_teacher_honor")
public class TeacherHonor {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teacherId;
    private String name;
    private String level;
    private String issuer;
    private LocalDate honorDate;
    private String fileUrl;
    private LocalDateTime createTime;
}
