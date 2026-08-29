package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** t_moment_student（微光·学生关联，联合主键，无自增 id） */
@Data
@TableName("t_moment_student")
public class MomentStudent {

    private Long momentId;
    private Long studentId;
}
