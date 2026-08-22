package com.aischool.server.service.report;

import com.aischool.server.common.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 渲染进程池：fork 独立 JVM 运行冻结的渲染核心 com.aischool.render.RenderPdf
 * （Thymeleaf + 页内 ECharts + Playwright 打印 A4 margin0），并发 4~8，不占 Web 线程。
 * 优先级：单份生成(0) 先于批量(1)，保证批量进行中单份仍 30s 内出稿。
 */
@Slf4j
@Service
public class RenderService {

    public static final int PRIORITY_SINGLE = 0;
    public static final int PRIORITY_BATCH = 1;

    private final ReportDataBuilder dataBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${aischool.render.renderer-home}")
    private String rendererHome;

    @Value("${aischool.render.concurrency}")
    private int concurrency;

    @Value("${aischool.render.timeout-seconds}")
    private int timeoutSeconds;

    @Value("${aischool.render.work-dir}")
    private String workDir;

    private ThreadPoolExecutor pool;

    public RenderService(ReportDataBuilder dataBuilder) {
        this.dataBuilder = dataBuilder;
    }

    @PostConstruct
    void init() {
        pool = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>());
        log.info("渲染进程池就绪：并发 {}，渲染核心 {}", concurrency, rendererHome);
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
    }

    /** 提交一次渲染（聚合 JSON → 子进程渲染），返回产物 PDF 路径 */
    public CompletableFuture<Path> submit(int priority, String batchKey, Long studentId, Long termId) {
        CompletableFuture<Path> future = new CompletableFuture<>();
        pool.execute(new PriorityTask(priority, () -> {
            try {
                future.complete(render(batchKey, studentId, termId));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }));
        return future;
    }

    private Path render(String batchKey, Long studentId, Long termId) throws Exception {
        Map<String, Object> data = dataBuilder.build(studentId, termId);
        Path dir = Paths.get(workDir, batchKey, String.valueOf(studentId));
        Files.createDirectories(dir);
        Path json = dir.resolve("data.json");
        Path pdf = dir.resolve("report.pdf");
        Path logFile = dir.resolve("render.log");
        Files.deleteIfExists(pdf);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(json.toFile(), data);

        Process process = new ProcessBuilder(javaExecutable(),
                        "-Dfile.encoding=UTF-8", "-Xmx512m", "-cp", rendererClasspath(),
                        "com.aischool.render.RenderPdf",
                        json.toAbsolutePath().toString(), pdf.toAbsolutePath().toString())
                .directory(new java.io.File(rendererHome))
                .redirectErrorStream(true)
                .start();
        // 子进程输出落日志文件（渲染器会打印 HTML:/PDF: 与 console 消息）
        try (BufferedWriter writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = process.getInputStream().read(buf)) >= 0) {
                writer.write(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
        }

        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new BizException(500, "渲染超时（>" + timeoutSeconds + "s），学生 " + studentId);
        }
        if (process.exitValue() != 0 || !Files.exists(pdf)) {
            throw new BizException(500, "渲染失败 exit=" + process.exitValue()
                    + "，日志尾部：" + tail(logFile));
        }
        Files.deleteIfExists(json);
        return pdf;
    }

    /** renderer-home/target/classes + target/lib/*.jar（部署时先 mvn package + copy-dependencies） */
    private String rendererClasspath() {
        Path home = Paths.get(rendererHome);
        StringBuilder cp = new StringBuilder(home.resolve("target/classes").toString());
        Path lib = home.resolve("target/lib");
        if (Files.isDirectory(lib)) {
            try (Stream<Path> jars = Files.list(lib)) {
                jars.filter(p -> p.getFileName().toString().endsWith(".jar"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(j -> cp.append(java.io.File.pathSeparatorChar).append(j));
            } catch (IOException e) {
                throw new BizException(500, "渲染核心 classpath 解析失败: " + e.getMessage());
            }
        }
        return cp.toString();
    }

    private String javaExecutable() {
        String javaHome = System.getProperty("java.home");
        String bin = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return Paths.get(javaHome, "bin", bin).toString();
    }

    private String tail(Path logFile) {
        try {
            String content = Files.readString(logFile, StandardCharsets.UTF_8);
            return content.length() > 600 ? "..." + content.substring(content.length() - 600) : content;
        } catch (IOException e) {
            return "(无日志)";
        }
    }

    /** 带优先级的队列任务：priority 小者先执行 */
    private record PriorityTask(int priority, Runnable body) implements Runnable, Comparable<PriorityTask> {

        @Override
        public void run() {
            body.run();
        }

        @Override
        public int compareTo(PriorityTask other) {
            return Integer.compare(priority, other.priority);
        }
    }
}
