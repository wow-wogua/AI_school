package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** t_talk 谈心记录（批11）：教师对可见班级学生，家长不可见 */
@Data
@TableName("t_talk")
public class Talk {

    public static final String TYPE_ACAD = "学业";
    public static final String TYPE_MIND = "心理";
    public static final String TYPE_DISCIPLINE = "纪律";
    public static final String TYPE_LIFE = "生活";
    public static final String TYPE_OTHER = "其他";
    public static final List<String> TYPES = List.of(TYPE_ACAD, TYPE_MIND, TYPE_DISCIPLINE, TYPE_LIFE, TYPE_OTHER);

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teacherId;
    private Long studentId;
    private LocalDate talkDate;
    private String talkType;
    private String content;
    private LocalDateTime createTime;
}
