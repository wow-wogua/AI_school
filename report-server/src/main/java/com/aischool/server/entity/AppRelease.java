package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** t_app_release（App 版本发布：管理端上传 APK，老师端 App 弹窗自助升级） */
@Data
@TableName("t_app_release")
public class AppRelease {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer versionCode;
    private String versionName;
    private String notes;
    private String fileUrl;
    private Long fileSize;
    private Integer forceFlag;
    private Long createdBy;
    private LocalDateTime createTime;
}
