package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** t_repair 报修单（批10）：文字描述+拍照凭证，提交→管理端处理 */
@Data
@TableName("t_repair")
public class Repair {

    public static final String PENDING = "PENDING";
    public static final String DONE = "DONE";
    public static final String REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reporterId;
    private String location;
    private String description;
    /** 凭证照片 objectName JSON 数组（≤3，MinIO repair/ 前缀） */
    private String photos;
    private String status;
    private Long handlerId;
    private String handleNote;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
}
