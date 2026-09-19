package com.aischool.server.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 领导端「教师使用情况」聚合（批2）：六类行为按教师分组计数。
 * 各表时间列口径不同（eval_time/create_time），统一以 since 起算；
 * 登录次数取自 t_audit_log 的 /api/auth/login 行（9-15 登录审计，成功行才回填 user_id）。
 */
public interface LeaderUsageMapper {

    @Select("""
            SELECT teacher_id AS userId, COUNT(*) AS cnt
            FROM t_evaluation WHERE eval_time >= #{since} AND teacher_id IS NOT NULL
            GROUP BY teacher_id
            """)
    List<Map<String, Object>> evalCount(@Param("since") LocalDateTime since);

    @Select("""
            SELECT created_by AS userId, COUNT(*) AS cnt
            FROM t_score WHERE create_time >= #{since} AND created_by IS NOT NULL
            GROUP BY created_by
            """)
    List<Map<String, Object>> scoreCount(@Param("since") LocalDateTime since);

    @Select("""
            SELECT teacher_id AS userId, COUNT(*) AS cnt
            FROM t_moment WHERE create_time >= #{since} AND teacher_id IS NOT NULL
            GROUP BY teacher_id
            """)
    List<Map<String, Object>> momentCount(@Param("since") LocalDateTime since);

    /** AI 报告生成次数与 tokens（与管理端 AI 用量同口径：仅走 LLM 的成功任务） */
    @Select("""
            SELECT created_by AS userId, COUNT(*) AS cnt,
                   IFNULL(SUM(prompt_tokens + completion_tokens), 0) AS tokens
            FROM t_ai_task
            WHERE source = 'llm' AND status = '成功' AND create_time >= #{since} AND created_by IS NOT NULL
            GROUP BY created_by
            """)
    List<Map<String, Object>> aiCount(@Param("since") LocalDateTime since);

    @Select("""
            SELECT user_id AS userId, COUNT(*) AS cnt, MAX(create_time) AS lastLogin
            FROM t_audit_log
            WHERE uri = '/api/auth/login' AND user_id IS NOT NULL AND create_time >= #{since}
            GROUP BY user_id
            """)
    List<Map<String, Object>> loginCount(@Param("since") LocalDateTime since);
}
