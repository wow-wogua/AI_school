package com.aischool.server.service.report;

import com.aischool.server.entity.GrowthLevel;
import com.aischool.server.mapper.GrowthLevelMapper;
import com.aischool.server.service.ai.AiImageClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 成长画像（批36① v2）：按「成长等级 + 最强素养格」拼提示词文生图，注入报告 p48。
 * 隐私红线：提示词不含学生姓名/学号等任何个人信息——全校最多 等级数×格数 张图
 * （当前 5×9=45），sha256(提示词+模型) 即缓存键：同一学生教师版/家长版/重新生成
 * 全部复用，等级或最强格不变就不调 API、不重复扣费。
 * 双层缓存：MinIO portrait/{sha}.{ext} 权威（容器重建不丢）+ 本地 cache-dir
 * 物化（渲染子进程同容器直接读文件）。
 * 任何失败只降级：返回 null → PDF 保持虚线占位框，绝不影响报告生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortraitService {

    private final AiImageClient aiImageClient;
    private final MinioClient minioClient;
    private final GrowthLevelMapper growthLevelMapper;

    @Value("${aischool.minio.bucket}")
    private String bucket;
    @Value("${aischool.portrait.cache-dir}")
    private String cacheDir;

    /**
     * 九格 → 画面元素主题词。实测定论（2026-10-07）：grok-imagine-image 中文提示词主体遵循
     * 0/4（画成少年/山羊），英文 3/3 全中且无文字无人物——提示词模板必须用英文。
     * 纯物件元素，不含人物/文字，避免诱发横幅标语。
     */
    private static final Map<String, String> SCENE_HINTS = Map.of(
            "人文底蕴", "open books and a rolled poetry scroll",
            "科学精神", "a starry night sky and a small telescope",
            "学会学习", "a warm desk lamp and neat notebooks",
            "健康生活", "sunshine, green leaves and flying birds",
            "责任担当", "several small plain red flags on poles",
            "实践创新", "gears and a small rocket lifting off",
            "学业扬长", "a golden trophy and pencils",
            "艺术特长", "paint brushes and colorful musical notes",
            "体育锻炼", "a running track and colorful balls");

    /** 成长等级 → 主体意象（symbol_name 可被学校改名，命中不上时用通用生长意象） */
    private static final Map<String, String> SYMBOL_IMAGERIES = Map.of(
            "种子", "a round seed resting on soft soil with a faint green glow",
            "嫩芽", "a tiny two-leaf sprout breaking through the soil with dew drops",
            "小树", "a young slender sapling with a thin brown trunk and sparse light-green leaves, growing upright toward the sun",
            "大树", "a lush leafy tree with a broad spreading crown",
            "栋梁", "a tall towering tree with a strong straight trunk reaching the sky");

    /** 同 hash 并发只放一个进 API（批渲染 6 并发撞同一张图时不重复扣费） */
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    /** 数据侧入口：grids/growthSymbol 即报告契约里的同源对象；返回本地图片绝对路径，降级返回 null */
    public String materialize(Map<String, Object> growthSymbol, List<Map<String, Object>> grids) {
        try {
            if (!aiImageClient.enabled()) {
                return null;
            }
            String prompt = buildPrompt(growthSymbol, grids);
            String model = aiImageClient.currentConfig().model();
            String hash = sha256(prompt + "|" + model);
            Path local = localFile(hash);
            if (local != null) {
                return local.toAbsolutePath().toString();
            }
            Object lock = keyLocks.computeIfAbsent(hash, k -> new Object());
            synchronized (lock) {
                local = localFile(hash); // 排队等到的再查一次，前一个线程可能已落盘
                if (local == null) {
                    byte[] bytes = fetchBytes(hash, prompt);
                    if (bytes == null) {
                        return null;
                    }
                    local = store(hash, bytes);
                }
            }
            return local.toAbsolutePath().toString();
        } catch (Exception e) {
            log.warn("成长画像生成失败（降级为占位框）: {}", e.getMessage());
            return null;
        }
    }

    /** MinIO 有则下载到本地，无则调 API 生成；失败返回 null（外层降级） */
    private byte[] fetchBytes(String hash, String prompt) {
        try {
            String prefix = "portrait/" + hash + ".";
            for (Result<Item> r : minioClient.listObjects(io.minio.ListObjectsArgs.builder()
                    .bucket(bucket).prefix(prefix).maxKeys(1).build())) {
                String object = r.get().objectName();
                try (var in = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(bucket).object(object).build())) {
                    return in.readAllBytes();
                }
            }
        } catch (Exception e) {
            log.warn("成长画像查缓存失败（转为直接生成）: {}", e.getMessage());
        }
        return aiImageClient.generate(prompt);
    }

    /** 落双层缓存：MinIO 权威 + 本地物化（临时文件原子挪过去，避免渲染读到半截） */
    private Path store(String hash, byte[] bytes) throws Exception {
        String ext = "image/png".equals(AiImageClient.magicOf(bytes)) ? "png" : "jpg";
        Path dir = Paths.get(cacheDir);
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, "tmp-", "." + ext);
        Files.write(tmp, bytes);
        Path target = dir.resolve(hash + "." + ext);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception atomicFail) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        try (var in = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object("portrait/" + hash + "." + ext)
                    .stream(in, bytes.length, -1)
                    .contentType(AiImageClient.magicOf(bytes))
                    .build());
        } catch (Exception e) {
            // MinIO 落库失败不致命：本地已有，本次能出图；容器重启后才会重花一次生图费
            log.warn("成长画像写 MinIO 失败（仅本地缓存生效）: {}", e.getMessage());
        }
        return target;
    }

    private Path localFile(String hash) {
        Path png = Paths.get(cacheDir).resolve(hash + ".png");
        if (Files.exists(png)) {
            return png;
        }
        Path jpg = Paths.get(cacheDir).resolve(hash + ".jpg");
        return Files.exists(jpg) ? jpg : null;
    }

    /** 提示词 = 等级主体意象 + 最强格主题词 + 风格约束；不含任何学生个人信息 */
    private String buildPrompt(Map<String, Object> growthSymbol, List<Map<String, Object>> grids) {
        int level = ((Number) growthSymbol.get("level")).intValue();
        List<GrowthLevel> levels = growthLevelMapper.selectList(new LambdaQueryWrapper<GrowthLevel>()
                .orderByAsc(GrowthLevel::getLevel));
        String symbol = levels.stream().filter(l -> l.getLevel() != null && l.getLevel() == level)
                .findFirst().map(GrowthLevel::getSymbolName).orElse("");
        String imagery = SYMBOL_IMAGERIES.getOrDefault(symbol, "a fresh green plant growing upward");
        String topGrid = grids == null ? "" : grids.stream()
                .filter(g -> g.get("cur") instanceof Map<?, ?> cur
                        && ((Map<?, ?>) cur).get("mine") instanceof Number)
                .max(Comparator.comparingDouble(g -> ((Number) ((Map<?, ?>) g.get("cur")).get("mine")).doubleValue()))
                .map(g -> String.valueOf(g.get("name")))
                .orElse("");
        // topGrid（中文格名）只当查表 key，本身不进提示词——英文模板里混中文会拉低遵循度
        String hint = SCENE_HINTS.getOrDefault(topGrid, "sunshine and fresh green leaves");
        return "Flat vector decorative illustration. In the center is " + imagery
                + ". Around it: " + hint
                + ". Simple flat color blocks, warm green and gold color palette, centered composition, clean plain background."
                + " Absolutely no text, no letters, no numbers, no banners, no people, no animals.";
    }

    private static String sha256(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(d);
    }
}
