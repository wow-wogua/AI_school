package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_content_item（内容发布：NOTICE 通知公告 / PARENTING 育儿课堂） */
@Data
@TableName("t_content_item")
public class ContentItem {

    /** 通知公告 */
    public static final String TYPE_NOTICE = "NOTICE";
    /** 育儿课堂 */
    public static final String TYPE_PARENTING = "PARENTING";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private String title;
    private String coverUrl;
    private String videoUrl;
    private String content;
    /** ALL=全校 / CLASS=指定班级 */
    private String scope;
    private Long classId;
    /** 1=已发布 0=未发布/已下架 */
    private Integer status;
    private LocalDateTime publishTime;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
