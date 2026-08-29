package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_moment（微光信箱·随手拍） */
@Data
@TableName("t_moment")
public class Moment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teacherId;
    private Long classId;
    private String photoUrl;
    private String note;
    private String sceneTag;
    private LocalDateTime createTime;
}
