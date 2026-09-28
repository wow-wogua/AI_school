package com.aischool.server.service.notify;

import com.aischool.server.entity.SysConfig;
import com.aischool.server.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 企业微信群机器人通道（批29）：t_sys_config 配 webhook 地址，POST markdown 消息。
 * 群消息只推「待办类」（新审批/新请假/新注册/告警/周报），审批结果不进群防刷屏。
 * 后续厂商推送（极光/个推）就绪后按同款配置键挂为第二通道。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WecomService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final SysConfigMapper sysConfigMapper;

    private String cfg(String key) {
        SysConfig c = sysConfigMapper.selectById(key);
        return c == null || c.getCfgValue() == null ? "" : c.getCfgValue().trim();
    }

    public boolean enabled() {
        return "1".equals(cfg("wecom_enabled")) && !cfg("wecom_webhook_url").isEmpty();
    }

    /** 审批类事件推群（wecom_push_approvals 总闸）；@Async 不阻塞业务请求 */
    @Async
    public void pushApprovals(String markdown) {
        if (!"1".equals(cfg("wecom_push_approvals"))) {
            return;
        }
        sendMarkdown(markdown);
    }

    /** 告警/周报类推群（不受审批闸控制，走 enabled 总闸） */
    public void sendMarkdown(String markdown) {
        String url = cfg("wecom_webhook_url");
        if (url.isEmpty() || !"1".equals(cfg("wecom_enabled"))) {
            return;
        }
        doSend(url, markdown);
    }

    /** 管理端「发送测试」：仅校验 URL 可达，不受 enabled 总闸（先测通再启用） */
    public String test(String url) {
        return doSend(url, "【石实SHINE】群机器人测试消息：连通正常 ✅");
    }

    private String doSend(String url, String markdown) {
        try {
            String body = "{\"msgtype\":\"markdown\",\"markdown\":{\"content\":"
                    + JSON.writeValueAsString(markdown.substring(0, Math.min(markdown.length(), 4000)))
                    + "}}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json;charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("企微机器人推送非 200：{} {}", resp.statusCode(), resp.body());
                return "HTTP " + resp.statusCode();
            }
            if (resp.body() != null && resp.body().contains("\"errcode\":0")) {
                return "ok";
            }
            log.warn("企微机器人返回异常：{}", resp.body());
            return resp.body() == null ? "无响应" : resp.body();
        } catch (Exception e) {
            log.warn("企微机器人推送失败：{}", e.getMessage());
            return e.getMessage() == null ? "失败" : e.getMessage();
        }
    }
}
