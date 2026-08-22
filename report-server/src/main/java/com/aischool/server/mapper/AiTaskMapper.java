package com.aischool.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aischool.server.entity.AiTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
