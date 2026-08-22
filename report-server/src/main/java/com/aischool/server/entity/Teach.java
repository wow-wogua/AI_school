package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** t_teach */
@Data
@TableName("t_teach")
public class Teach {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teacherId;
    private Long classId;
    private Long subjectId;
}
