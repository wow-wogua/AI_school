package com.aischool.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aischool.server.entity.AiTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AiTaskMapper extends BaseMapper<AiTask> {

    /** 我的近期任务（带学生姓名，供全局任务面板展示） */
    @Select("""
            SELECT t.id AS taskId, t.task_type AS taskType, t.student_id AS studentId, s.name AS studentName,
                   t.term_id AS termId, t.status, t.source, t.error,
                   t.create_time AS createTime, t.started_time AS startedTime, t.finished_time AS finishedTime
            FROM t_ai_task t JOIN t_student s ON s.id = t.student_id
            WHERE t.created_by = #{userId}
            ORDER BY t.id DESC LIMIT #{limit}
            """)
    List<Map<String, Object>> selectMine(@Param("userId") Long userId, @Param("limit") int limit);

    /** 指定任务的前方排队数（含自身，用于显示"第 N 位"） */
    @Select("SELECT COUNT(*) FROM t_ai_task WHERE status = '排队' AND id <= #{id}")
    int queuePosition(@Param("id") Long id);

    /** AI 用量：按日聚合（仅走 LLM 的成功任务；day 为 yyyy-MM-dd 字符串） */
    @Select("""
            SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS day, COUNT(*) AS tasks,
                   IFNULL(SUM(prompt_tokens), 0) AS promptTokens,
                   IFNULL(SUM(completion_tokens), 0) AS completionTokens
            FROM t_ai_task
            WHERE source = 'llm' AND status = '成功' AND create_time >= #{since}
            GROUP BY day ORDER BY day DESC
            """)
    List<Map<String, Object>> usageByDay(@Param("since") LocalDateTime since);

    /** AI 用量：按教师聚合（谁在用、用了多少） */
    @Select("""
            SELECT u.real_name AS teacher, COUNT(*) AS tasks,
                   IFNULL(SUM(t.prompt_tokens), 0) AS promptTokens,
                   IFNULL(SUM(t.completion_tokens), 0) AS completionTokens
            FROM t_ai_task t JOIN t_user u ON u.id = t.created_by
            WHERE t.source = 'llm' AND t.status = '成功' AND t.create_time >= #{since}
            GROUP BY t.created_by, u.real_name ORDER BY promptTokens DESC
            """)
    List<Map<String, Object>> usageByTeacher(@Param("since") LocalDateTime since);
}
