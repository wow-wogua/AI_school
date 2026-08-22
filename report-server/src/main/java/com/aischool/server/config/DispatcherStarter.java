package com.aischool.server.config;

import com.aischool.server.service.report.ReportTaskService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 应用就绪后启动报告任务派发线程 */
@Component
@RequiredArgsConstructor
public class DispatcherStarter {

    private final ReportTaskService reportTaskService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        reportTaskService.startDispatcher();
    }

    @PreDestroy
    public void onShutdown() {
        reportTaskService.stopDispatcher();
    }
}
