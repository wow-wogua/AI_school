package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_report */
@Data
@TableName("t_report")
public class Report {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long studentId;
    private Long termId;
    /** 报告类型（批26）：TERM 学期=存量默认 / YEAR 学年 / SCHOOL 在校 */
    private String scopeType;
    /** 覆盖学期 id 快照（逗号串；TERM=null 单学期即 termId） */
    private String termIds;
    private String fileUrl;
    /** 批5 家长版 PDF 对象名（去成绩板块；null=未生成/渲染失败，教师版不受影响） */
    private String parentFileUrl;
    private Integer pageCount;
    private LocalDateTime genTime;
    private String status;
    private String error;
    private LocalDateTime createTime;
}
