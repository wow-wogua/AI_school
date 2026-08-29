package com.aischool.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * M5 最小验证：黄金学生 JSON → Thymeleaf HTML（页内 ECharts）→ Playwright 打印 A4 PDF。
 * 用法：java ... RenderPdf [数据json路径] [输出pdf路径]
 */
public class RenderPdf {

    public static void main(String[] args) throws Exception {
        Path jsonPath = Paths.get(args.length > 0 ? args[0] : "src/main/resources/golden_student.json");
        Path outPdf = Paths.get(args.length > 1 ? args[1] : "target/report.pdf");

        // ① 读取报告数据
        ObjectMapper om = new ObjectMapper();
        Map<String, Object> data = om.readValue(Files.readString(jsonPath, StandardCharsets.UTF_8), Map.class);

        // ② Thymeleaf 组装 HTML
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);

        Context ctx = new Context();
        ctx.setVariable("r", data);
        ctx.setVariable("dataJson", om.writeValueAsString(data));
        // echarts 内联进 HTML，避免 file:// 相对路径问题
        ctx.setVariable("echartsJs", resourceText("/static/echarts.min.js"));
        // 模板原版图片（提取自 学生成长报告册.pdf）同样内联为 data URI
        ctx.setVariable("imgIdeaBg", dataUri("/static/img/img_idea_bg.jpg"));
        ctx.setVariable("imgPhoto1", dataUri("/static/img/img_photo1.jpg"));
        ctx.setVariable("imgPhoto2", dataUri("/static/img/img_photo2.jpg"));
        ctx.setVariable("imgNineGrid", dataUri("/static/img/img_nine_grid.png"));
        ctx.setVariable("imgPrincipal", dataUri("/static/img/img_principal.png"));
        ctx.setVariable("imgCornerTl", dataUri("/static/img/img_corner_tl.png"));
        ctx.setVariable("imgCornerBr", dataUri("/static/img/img_corner_br.png"));
        ctx.setVariable("imgDeco", dataUri("/static/img/img_deco.png"));
        ctx.setVariable("imgCornerTr", dataUri("/static/img/img_corner_tr.png"));
        ctx.setVariable("imgIconL", dataUri("/static/img/img_icon_l.png"));
        ctx.setVariable("imgIconR", dataUri("/static/img/img_icon_r.png"));
        ctx.setVariable("imgLogo", dataUri("/static/img/img_logo.png"));
        String html = engine.process("report", ctx);

        Files.createDirectories(outPdf.toAbsolutePath().getParent());
        Path htmlOut = outPdf.resolveSibling(
                outPdf.getFileName().toString().replaceAll("\\.pdf$", "") + ".html");
        Files.writeString(htmlOut, html, StandardCharsets.UTF_8);
        System.out.println("HTML: " + htmlOut);

        // ③ Playwright 打印 PDF（页眉/页码由模板每页 CSS 绝对定位渲染，margin 全 0）
        try (Playwright pw = Playwright.create()) {
            // --no-sandbox：Linux 容器内以 root 运行 Chromium 必需；Windows 宿主机无影响
            Browser browser = pw.chromium().launch(
                    new BrowserType.LaunchOptions().setArgs(List.of("--no-sandbox")));
            Page page = browser.newPage();
            page.onConsoleMessage(msg -> System.out.println("[console." + msg.type() + "] " + msg.text()));
            page.onPageError(err -> System.out.println("[pageerror] " + err));
            page.navigate(htmlOut.toAbsolutePath().toUri().toString());
            // 等待页内所有 ECharts 完成渲染（模板 finally 置 window.chartsReady = true）
            try {
                page.waitForFunction("window.chartsReady === true");
            } catch (Exception timeout) {
                Object diag = page.evaluate("() => ({ready: window.chartsReady, echarts: typeof echarts,"
                        + " err: window.__chartErr || null})");
                System.out.println("[diag] " + diag);
                throw timeout;
            }
            page.pdf(new Page.PdfOptions()
                    .setPath(outPdf)
                    .setFormat("A4")
                    .setPrintBackground(true)
                    .setMargin(new com.microsoft.playwright.options.Margin()
                            .setTop("0").setBottom("0").setLeft("0").setRight("0")));
            browser.close();
        }
        System.out.println("PDF : " + outPdf);
    }

    private static String resourceText(String path) throws Exception {
        return new String(RenderPdf.class.getResourceAsStream(path).readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String dataUri(String path) throws Exception {
        byte[] bytes = RenderPdf.class.getResourceAsStream(path).readAllBytes();
        String mime = path.endsWith(".png") ? "image/png" : "image/jpeg";
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
