package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_sys_config（系统开关·键值） */
@Data
@TableName("t_sys_config")
public class SysConfig {

    @TableId
    private String cfgKey;
    private String cfgValue;
    private LocalDateTime updateTime;
}
