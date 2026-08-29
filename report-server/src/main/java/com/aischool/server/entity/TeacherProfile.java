package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** t_teacher_profile（教师档案；主键=账号 id，由调用方传入而非生成） */
@Data
@TableName("t_teacher_profile")
public class TeacherProfile {

    @TableId(type = IdType.INPUT)
    private Long userId;
    private String employeeNo;
    private String gender;
    private Long subjectId;
    private String title;
    private String duty;
    private Integer teachingYears;
    private String photoUrl;
    private String intro;
    private LocalDate hireDate;
    private LocalDateTime updateTime;
}
