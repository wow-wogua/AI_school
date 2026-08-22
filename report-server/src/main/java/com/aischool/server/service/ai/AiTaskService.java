package com.aischool.server.service.ai;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.AiTask;
import com.aischool.server.mapper.AiTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * AI 分析任务队列：worker 池（默认 8 并发）消费 t_ai_task，提交即返回 taskId，前端轮询。
 * 与报告渲染（concurrency 池化）同模式——LLM 调用 5~20s 属 IO 等待，池大小只需匹配
 * 供应商速率限制，并发上限之上自动排队削峰（全年级批量 = 排队消化，不堆积连接）。
 * 重复提交同一学生同类型任务自动去重（返回既有 taskId，省 API 账单）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskService {

    private final AiTaskMapper taskMapper;
    private final AiDraftService draftService;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${aischool.ai.concurrency:8}")
    private int concurrency;

    private ThreadPoolExecutor pool;

    @PostConstruct
    public void init() {
        pool = new ThreadPoolExecutor(concurrency, concurrency, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10_000), r -> {
                    Thread t = new Thread(r, "ai-task-worker");
                    t.setDaemon(true);
                    return t;
                });
        // 重启恢复：中断的任务重置回队列（容器重启内存队列会清空，以 DB 为准重新入队）
        int recovered = 0;
        for (AiTask t : taskMapper.selectList(new LambdaQueryWrapper<AiTask>()
                .in(AiTask::getStatus, "排队", "生成中")
                .orderByAsc(AiTask::getId))) {
            if ("生成中".equals(t.getStatus())) {
                t.setStatus("排队");
                taskMapper.updateById(t);
            }
            final Long id = t.getId();
            pool.execute(() -> run(id));
            recovered++;
        }
        if (recovered > 0) {
            log.info("AI 任务恢复：重新入队 {} 条", recovered);
        }
    }

    /** 提交任务；同学生同类型同学期已有未完成任务时直接复用（去重） */
    public Long submit(String type, Long studentId, Long termId, Long userId) {
        if (!AiTask.COMMENT.equals(type) && !AiTask.SUMMARY.equals(type)) {
            throw new BizException(400, "task_type 仅支持 COMMENT / SUMMARY");
        }
        AiTask exist = taskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getTaskType, type)
                .eq(AiTask::getStudentId, studentId)
                .eq(AiTask::getTermId, termId)
                .in(AiTask::getStatus, "排队", "生成中")
                .orderByDesc(AiTask::getId).last("LIMIT 1"));
        if (exist != null) {
            return exist.getId();
        }
        AiTask t = new AiTask();
        t.setTaskType(type);
        t.setStudentId(studentId);
        t.setTermId(termId);
        t.setStatus("排队");
        t.setCreatedBy(userId);
        taskMapper.insert(t);
        final Long id = t.getId();
        pool.execute(() -> run(id));
        return id;
    }

    /** worker：执行并落结果。AI 调用失败由 AiDraftService 内部降级为模板（任务仍算成功） */
    private void run(Long id) {
        AiTask t = taskMapper.selectById(id);
        if (t == null || !"排队".equals(t.getStatus())) {
            return; // 已被处理（如重复入队），幂等退出
        }
        t.setStatus("生成中");
        t.setStartedTime(LocalDateTime.now());
        taskMapper.updateById(t);
        try {
            Map<String, Object> result = AiTask.COMMENT.equals(t.getTaskType())
                    ? draftService.commentDraft(t.getStudentId(), t.getTermId())
                    : draftService.summaryDraft(t.getStudentId(), t.getTermId());
            t.setSource(String.valueOf(result.get("source")));
            t.setResultJson(om.writeValueAsString(result));
            t.setStatus("成功");
        } catch (Exception e) {
            log.warn("AI 任务 #{} 失败: {}", id, e.getMessage());
            t.setStatus("失败");
            t.setError(e.getMessage() == null ? "未知错误" : e.getMessage().substring(0, Math.min(500, e.getMessage().length())));
        }
        t.setFinishedTime(LocalDateTime.now());
        taskMapper.updateById(t);
    }

    public AiTask get(Long id) {
        return taskMapper.selectById(id);
    }

    public List<Map<String, Object>> mine(Long userId, int limit) {
        return taskMapper.selectMine(userId, Math.min(Math.max(limit, 1), 200));
    }

    public int queuePosition(Long id) {
        return taskMapper.queuePosition(id);
    }
}
