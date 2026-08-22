package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;

/** t_student */
@Data
@TableName("t_student")
public class Student {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String studentNo;
    private String name;
    private String gender;
    private Long classId;
    private LocalDate enrollDate;
    private String status;
    private String photoUrl;
    private String guardianName;
    private String guardianPhone;
}
