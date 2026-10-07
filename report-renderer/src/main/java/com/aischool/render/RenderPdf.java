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
 * 用法：java ... RenderPdf [数据json路径] [输出pdf路径] [mode]
 * mode=teacher（学期报告教师版，默认）/ parent（批5 家长版去成绩板块）
 *     / year|school（批26 学年/在校报告，模板 report-annual，单版本）
 *     / footprint（批26 教师足迹报告，模板 footprint）。
 */
public class RenderPdf {

    public static void main(String[] args) throws Exception {
        Path jsonPath = Paths.get(args.length > 0 ? args[0] : "src/main/resources/golden_student.json");
        Path outPdf = Paths.get(args.length > 1 ? args[1] : "target/report.pdf");
        String mode = args.length > 2 ? args[2] : "teacher";
        // 批35 素材库：管理端上传的 PDF 图位覆盖目录（{key}.{jpg|png}），存在即优先于 classpath 内置
        Path assetOverrides = args.length > 3 ? Paths.get(args[3]) : null;
        boolean parentEdition = "parent".equals(mode);
        boolean annual = "year".equals(mode) || "school".equals(mode);
        boolean footprint = "footprint".equals(mode);

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
        ctx.setVariable("parentEdition", parentEdition);
        ctx.setVariable("scopeLabel", "school".equals(mode) ? "在校报告" : "学年报告");
        ctx.setVariable("dataJson", om.writeValueAsString(data));
        // echarts 内联进 HTML，避免 file:// 相对路径问题
        ctx.setVariable("echartsJs", resourceText("/static/echarts.min.js"));
        // 模板原版图片（提取自 学生成长报告册.pdf）同样内联为 data URI；
        // 素材库覆盖目录（批35）优先：管理端上传的 img_idea_bg/img_photo1/img_photo2/img_nine_grid/img_principal
        ctx.setVariable("imgIdeaBg", dataUri(assetOverrides, "img_idea_bg", "/static/img/img_idea_bg.jpg"));
        ctx.setVariable("imgPhoto1", dataUri(assetOverrides, "img_photo1", "/static/img/img_photo1.jpg"));
        ctx.setVariable("imgPhoto2", dataUri(assetOverrides, "img_photo2", "/static/img/img_photo2.jpg"));
        ctx.setVariable("imgNineGrid", dataUri(assetOverrides, "img_nine_grid", "/static/img/img_nine_grid.png"));
        ctx.setVariable("imgPrincipal", dataUri(assetOverrides, "img_principal", "/static/img/img_principal.png"));
        ctx.setVariable("imgCornerTl", dataUri("/static/img/img_corner_tl.png"));
        ctx.setVariable("imgCornerBr", dataUri("/static/img/img_corner_br.png"));
        ctx.setVariable("imgDeco", dataUri("/static/img/img_deco.png"));
        ctx.setVariable("imgCornerTr", dataUri("/static/img/img_corner_tr.png"));
        ctx.setVariable("imgIconL", dataUri("/static/img/img_icon_l.png"));
        ctx.setVariable("imgIconR", dataUri("/static/img/img_icon_r.png"));
        ctx.setVariable("imgLogo", dataUri("/static/img/img_logo.png"));
        // 批36① AI 成长画像：服务端物化的本地图片内联为 dataUri（null/读不到 → 模板保持虚线占位框）；
        // 渲染器不依赖 server 模块，魔数判 mime 就地写一份
        String portraitUri = null;
        Object portraitFile = data.get("portraitFile");
        if (portraitFile instanceof String p && !p.isBlank()) {
            byte[] b = Files.readAllBytes(Paths.get(p));
            if (b.length > 4) {
                String mime = (b[0] & 0xFF) == 0x89 && (b[1] & 0xFF) == 0x50 ? "image/png" : "image/jpeg";
                portraitUri = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(b);
            }
        }
        ctx.setVariable("portraitUri", portraitUri);
        String html = engine.process(footprint ? "footprint" : annual ? "report-annual" : "report", ctx);

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

    /** 素材库覆盖优先（批35）：覆盖目录存在 {key}.png / {key}.jpg 即用之，否则 classpath 内置 */
    private static String dataUri(Path overrides, String key, String builtin) throws Exception {
        if (overrides != null) {
            for (String ext : List.of("png", "jpg")) {
                Path f = overrides.resolve(key + "." + ext);
                if (Files.exists(f)) {
                    byte[] bytes = Files.readAllBytes(f);
                    String mime = "png".equals(ext) ? "image/png" : "image/jpeg";
                    return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
                }
            }
        }
        return dataUri(builtin);
    }
}
