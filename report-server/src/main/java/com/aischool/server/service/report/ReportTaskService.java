package com.aischool.server.service.report;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.*;
import com.aischool.server.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 报告任务：Redis 队列 + 派发线程 + t_report_task/t_report 生命周期 + Redis 进度缓存。
 * 状态机：任务 排队→进行中→成功/部分失败/失败；明细 排队→渲染中→成功/失败（失败可重试，复用原行）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportTaskService {

    public static final String QUEUE_KEY = "report:task:q";
    private static final String TASK_KEY = "report:task:";
    private static final Duration PROGRESS_TTL = Duration.ofHours(24);

    private final ReportTaskMapper taskMapper;
    private final ReportMapper reportMapper;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final GradeMapper gradeMapper;
    private final TermMapper termMapper;
    private final RenderService renderService;
    private final PdfStoreService pdfStoreService;
    private final StringRedisTemplate redis;

    private Thread dispatcher;
    private volatile boolean running = false;

    public void startDispatcher() {
        recoverInterrupted();
        running = true;
        dispatcher = new Thread(this::dispatchLoop, "report-task-dispatcher");
        dispatcher.setDaemon(true);
        dispatcher.start();
        log.info("报告任务派发线程已启动");
    }

    public void stopDispatcher() {
        running = false;
    }

    /** 服务重启断点续跑：渲染中的明细/进行中的任务回队 */
    private void recoverInterrupted() {
        List<Report> stuck = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .eq(Report::getStatus, "渲染中"));
        for (Report r : stuck) {
            r.setStatus("排队");
            r.setError(null);
            reportMapper.updateById(r);
        }
        List<ReportTask> runningTasks = taskMapper.selectList(new LambdaQueryWrapper<ReportTask>()
                .eq(ReportTask::getStatus, "进行中"));
        for (ReportTask t : runningTasks) {
            t.setStatus("排队");
            taskMapper.updateById(t);
            redis.opsForList().leftPush(QUEUE_KEY, String.valueOf(t.getId()));
        }
        if (!stuck.isEmpty() || !runningTasks.isEmpty()) {
            log.info("断点续跑：{} 条明细、{} 个任务重新入队", stuck.size(), runningTasks.size());
        }
    }

    private void dispatchLoop() {
        while (running) {
            try {
                String taskId = redis.opsForList().leftPop(QUEUE_KEY, 5, java.util.concurrent.TimeUnit.SECONDS);
                if (taskId != null) {
                    dispatch(Long.parseLong(taskId));
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    return;
                }
                log.error("任务派发异常", e);
            }
        }
    }

    // ───────────────── 任务创建 ─────────────────

    public ReportTask createTask(String scope, Long targetId, Long termId, Long createBy) {
        Term term = termMapper.selectById(termId);
        if (term == null) {
            throw new BizException(404, "学期不存在");
        }
        List<Long> studentIds = switch (scope) {
            case "单生" -> {
                Student s = studentMapper.selectById(targetId);
                if (s == null) {
                    throw new BizException(404, "学生不存在");
                }
                yield List.of(targetId);
            }
            case "班级" -> studentMapper.selectList(new LambdaQueryWrapper<Student>()
                            .eq(Student::getClassId, targetId).orderByAsc(Student::getStudentNo))
                    .stream().map(Student::getId).toList();
            case "年级" -> {
                List<Long> classIds = clazzMapper.selectList(new LambdaQueryWrapper<Clazz>()
                                .eq(Clazz::getGradeId, targetId)).stream().map(Clazz::getId).toList();
                yield classIds.isEmpty() ? List.<Long>of() : studentMapper.selectList(
                        new LambdaQueryWrapper<Student>().in(Student::getClassId, classIds)
                                .orderByAsc(Student::getStudentNo)).stream().map(Student::getId).toList();
            }
            default -> throw new BizException(400, "scope 必须为 单生/班级/年级");
        };
        if (studentIds.isEmpty()) {
            throw new BizException(400, "目标范围内没有学生");
        }

        ReportTask task = new ReportTask();
        task.setTermId(termId);
        task.setScope(scope);
        task.setTargetId(targetId);
        task.setStatus("排队");
        task.setTotal(studentIds.size());
        task.setDone(0);
        task.setFailed(0);
        task.setCreateBy(createBy);
        taskMapper.insert(task);

        for (Long studentId : studentIds) {
            Report item = new Report();
            item.setTaskId(task.getId());
            item.setStudentId(studentId);
            item.setTermId(termId);
            item.setStatus("排队");
            reportMapper.insert(item);
        }
        writeProgress(task);
        redis.opsForList().leftPush(QUEUE_KEY, String.valueOf(task.getId()));
        return task;
    }

    private void dispatch(Long taskId) {
        ReportTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setStatus("进行中");
        taskMapper.updateById(task);
        writeProgress(task);

        List<Report> items = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .eq(Report::getTaskId, taskId)
                .in(Report::getStatus, "排队", "失败"));
        if (items.isEmpty()) {
            finalizeTask(taskId);
            return;
        }
        int priority = "单生".equals(task.getScope()) ? RenderService.PRIORITY_SINGLE : RenderService.PRIORITY_BATCH;
        // 逐项挂完成回调：成功→归档+计数；失败→记错+计数
        var callbacks = items.stream()
                .map(item -> renderService.submit(priority, String.valueOf(taskId), item.getStudentId(), task.getTermId())
                        .whenComplete((pdf, err) -> finalizeItem(taskId, item, pdf, err)))
                .toList();
        CompletableFuture.allOf(callbacks.toArray(new CompletableFuture[0]))
                .whenComplete((v, err) -> finalizeTask(taskId));
        log.info("任务 {} 已派发 {} 份（scope={}）", taskId, items.size(), task.getScope());
    }

    private synchronized void finalizeItem(Long taskId, Report item, Path pdf, Throwable err) {
        Report current = reportMapper.selectById(item.getId());
        if (current == null || "成功".equals(current.getStatus())) {
            return; // 重试后旧回调晚到，忽略
        }
        if (err != null) {
            current.setStatus("失败");
            current.setError(rootMessage(err));
            reportMapper.updateById(current);
            bumpCounter(taskId, "failed", 1);
            log.warn("任务 {} 学生 {} 渲染失败: {}", taskId, item.getStudentId(), current.getError());
            return;
        }
        String objectName = "reports/" + item.getTermId() + "/" + item.getStudentId() + "/" + item.getId() + ".pdf";
        try {
            pdfStoreService.upload(objectName, pdf);
        } catch (Exception e) {
            current.setStatus("失败");
            current.setError("归档失败: " + e.getMessage());
            reportMapper.updateById(current);
            bumpCounter(taskId, "failed", 1);
            return;
        }
        current.setStatus("成功");
        current.setFileUrl(objectName);
        current.setGenTime(LocalDateTime.now());
        reportMapper.updateById(current);
        bumpCounter(taskId, "done", 1);
        try {
            java.nio.file.Files.deleteIfExists(pdf);
            java.nio.file.Files.deleteIfExists(pdf.resolveSibling("report.html"));
        } catch (Exception ignore) {
        }
    }

    private synchronized void finalizeTask(Long taskId) {
        ReportTask task = taskMapper.selectById(taskId);
        if (task == null || "成功".equals(task.getStatus()) || "失败".equals(task.getStatus())
                || "部分失败".equals(task.getStatus())) {
            return;
        }
        List<Report> items = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .eq(Report::getTaskId, taskId));
        long done = items.stream().filter(i -> "成功".equals(i.getStatus())).count();
        long failed = items.stream().filter(i -> "失败".equals(i.getStatus())).count();
        task.setDone((int) done);
        task.setFailed((int) failed);
        task.setStatus(failed == 0 ? "成功" : done > 0 ? "部分失败" : "失败");
        taskMapper.updateById(task);
        writeProgress(task);
        log.info("任务 {} 完成：成功 {} / 失败 {} / 共 {}", taskId, done, failed, items.size());
    }

    /** 失败重试：失败明细回队，进度重算 */
    public ReportTask retryTask(Long taskId, Long userId) {
        ReportTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "任务不存在");
        }
        if (!"部分失败".equals(task.getStatus()) && !"失败".equals(task.getStatus())) {
            throw new BizException(400, "任务当前状态不可重试");
        }
        List<Report> failed = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .eq(Report::getTaskId, taskId).eq(Report::getStatus, "失败"));
        for (Report r : failed) {
            r.setStatus("排队");
            r.setError(null);
            reportMapper.updateById(r);
        }
        task.setStatus("排队");
        task.setFailed(0);
        taskMapper.updateById(task);
        writeProgress(task);
        redis.opsForList().leftPush(QUEUE_KEY, String.valueOf(taskId));
        return task;
    }

    // ───────────────── 进度（Redis 优先，DB 兜底） ─────────────────

    private void writeProgress(ReportTask task) {
        try {
            String key = TASK_KEY + task.getId();
            redis.opsForHash().putAll(key, Map.of(
                    "status", task.getStatus(),
                    "total", String.valueOf(task.getTotal()),
                    "done", String.valueOf(task.getDone()),
                    "failed", String.valueOf(task.getFailed())));
            redis.expire(key, PROGRESS_TTL);
        } catch (Exception e) {
            log.warn("进度写 Redis 失败（降级读库）: {}", e.getMessage());
        }
    }

    private void bumpCounter(Long taskId, String field, int delta) {
        try {
            redis.opsForHash().increment(TASK_KEY + taskId, field, delta);
            redis.expire(TASK_KEY + taskId, PROGRESS_TTL);
        } catch (Exception ignore) {
        }
    }

    public Map<String, Object> progress(Long taskId) {
        ReportTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "任务不存在");
        }
        Map<Object, Object> cached = redis.opsForHash().entries(TASK_KEY + taskId);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("taskId", taskId);
        m.put("status", cached.containsKey("status") ? cached.get("status") : task.getStatus());
        m.put("total", task.getTotal());
        m.put("done", toLong(cached.get("done"), task.getDone()));
        m.put("failed", toLong(cached.get("failed"), task.getFailed()));
        return m;
    }

    private long toLong(Object cached, Integer fallback) {
        if (cached != null) {
            try {
                return Long.parseLong(String.valueOf(cached));
            } catch (NumberFormatException ignore) {
            }
        }
        return fallback != null ? fallback : 0;
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null ? cur.getClass().getSimpleName() : msg.substring(0, Math.min(msg.length(), 490));
    }
}
