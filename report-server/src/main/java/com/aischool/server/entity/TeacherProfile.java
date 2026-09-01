package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    /** 档案保存是全字段覆盖语义：updateById 默认跳过 null 会让"清空字段"假成功，故业务字段 null 也写入 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String employeeNo;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String gender;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long subjectId;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String title;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String duty;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer teachingYears;
    private String photoUrl;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String intro;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate hireDate;
    private LocalDateTime updateTime;
}
