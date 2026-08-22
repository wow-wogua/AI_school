package com.aischool.server.service;

import com.aischool.server.entity.AiTask;
import com.aischool.server.entity.AuditLog;
import com.aischool.server.mapper.AiTaskMapper;
import com.aischool.server.mapper.AuditLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 定时数据保留：AI 已完成任务留 90 天、审计日志留 180 天（aischool.retention.* 可配） */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final AiTaskMapper aiTaskMapper;
    private final AuditLogMapper auditLogMapper;

    @Value("${aischool.retention.task-days:90}")
    private int taskDays;

    @Value("${aischool.retention.audit-days:180}")
    private int auditDays;

    /** 每日 03:17 清理（避开整点业务高峰） */
    @Scheduled(cron = "0 17 3 * * ?")
    public void cleanup() {
        LocalDateTime taskCutoff = LocalDateTime.now().minusDays(taskDays);
        int tasks = aiTaskMapper.delete(new LambdaQueryWrapper<AiTask>()
                .in(AiTask::getStatus, List.of("成功", "失败"))
                .lt(AiTask::getCreateTime, taskCutoff));
        int audits = auditLogMapper.delete(new LambdaQueryWrapper<AuditLog>()
                .lt(AuditLog::getCreateTime, LocalDateTime.now().minusDays(auditDays)));
        if (tasks > 0 || audits > 0) {
            log.info("数据保留清理：AI 任务 {} 条（>{}天），审计日志 {} 条（>{}天）", tasks, taskDays, audits, auditDays);
        }
    }
}
