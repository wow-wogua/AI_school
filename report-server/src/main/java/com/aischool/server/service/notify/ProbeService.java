package com.aischool.server.service.notify;

import com.aischool.server.entity.Notification;
import com.aischool.server.entity.SysConfig;
import com.aischool.server.mapper.NotificationMapper;
import com.aischool.server.mapper.SysConfigMapper;
import com.aischool.server.service.report.PdfStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务器探活（批29）：每分钟醒一次按配置间隔巡检 数据库/MinIO/磁盘余量，
 * 状态变化才告警（故障一次+恢复一次，不重复轰炸）；企微群 + 管理员 App 通知双通道。
 * 进程自身的存活由外部探针打免登 /api/ping 监控（服务器挂了本服务无从自报）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProbeService {

    private static final double DISK_FREE_FLOOR = 0.10; // 剩余 <10% 告警

    private final NotificationMapper notificationMapper;
    private final SysConfigMapper sysConfigMapper;
    private final PdfStoreService pdfStore;
    private final WecomService wecomService;
    private final NotificationService notificationService;

    /** 各检查项上次状态（null=未观测过，首跑只立基线不告警） */
    private final Map<String, Boolean> lastState = new HashMap<>();
    private volatile long lastTick;

    /** 巡检：DB / MinIO / 磁盘；手动触发端点与定时器共用 */
    public List<Map<String, Object>> runProbe() {
        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(check("db", "数据库", probeDb()));
        checks.add(check("minio", "MinIO 文件存储", pdfStore.healthy()));
        checks.add(probeDisk());
        for (Map<String, Object> c : checks) {
            transition((String) c.get("key"), (String) c.get("label"), Boolean.TRUE.equals(c.get("ok")), c.get("detail"));
        }
        return checks;
    }

    /** 状态迁移才动作：好→坏告警，坏→好报恢复 */
    private void transition(String key, String label, boolean ok, Object detail) {
        Boolean last = lastState.get(key);
        lastState.put(key, ok);
        if (last == null || last == ok) {
            return;
        }
        String time = LocalDateTime.now().withNano(0).toString().replace('T', ' ');
        if (!ok) {
            String md = "【探活告警】" + label + " 异常：" + detail + "（" + time + "）";
            wecomService.sendMarkdown(md);
            notificationService.sendToRoles(List.of("ADMIN"), Notification.ALERT, "探活告警：" + label + "异常",
                    String.valueOf(detail), null);
            log.warn(md);
        } else {
            wecomService.sendMarkdown("【探活恢复】" + label + " 已恢复正常（" + time + "）");
            notificationService.sendToRoles(List.of("ADMIN"), Notification.ALERT, "探活恢复：" + label,
                    label + " 已恢复正常", null);
        }
    }

    private Map<String, Object> check(String key, String label, boolean ok) {
        Map<String, Object> m = new HashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("ok", ok);
        m.put("detail", ok ? "正常" : "无响应");
        return m;
    }

    private boolean probeDb() {
        try {
            return notificationMapper.selectCount(null) >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> probeDisk() {
        Map<String, Object> m = new HashMap<>();
        m.put("key", "disk");
        m.put("label", "磁盘余量");
        try {
            var store = Files.getFileStore(Path.of("/"));
            long total = store.getTotalSpace(), usable = store.getUsableSpace();
            boolean ok = usable > total * DISK_FREE_FLOOR;
            m.put("ok", ok);
            m.put("detail", "剩余 " + gb(usable) + "G / 共 " + gb(total) + "G" + (ok ? "" : "，不足 10%"));
        } catch (Exception e) {
            m.put("ok", true); // 容器内取不到文件系统信息不算故障（不误报）
            m.put("detail", "无法读取（" + e.getMessage() + "）");
        }
        return m;
    }

    private static String gb(long bytes) {
        return String.format("%.0f", bytes / 1024.0 / 1024 / 1024);
    }

    private String cfg(String key) {
        SysConfig c = sysConfigMapper.selectById(key);
        return c == null || c.getCfgValue() == null ? "" : c.getCfgValue().trim();
    }

    /** 每分钟醒一次，按配置间隔（默认 5 分钟）真正巡检；开关关闭即跳过 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 120_000)
    public void tick() {
        if (!"1".equals(cfg("alert_enabled"))) {
            return;
        }
        int intervalMin;
        try {
            intervalMin = Math.max(1, Integer.parseInt(cfg("alert_interval_min")));
        } catch (NumberFormatException e) {
            intervalMin = 5;
        }
        long now = System.currentTimeMillis();
        if (now - lastTick < intervalMin * 60_000L) {
            return;
        }
        lastTick = now;
        try {
            runProbe();
        } catch (Exception e) {
            log.warn("探活巡检异常：{}", e.getMessage());
        }
    }
}
