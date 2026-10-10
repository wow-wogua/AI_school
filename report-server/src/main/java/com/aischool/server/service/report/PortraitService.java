package com.aischool.server.service.report;

import com.aischool.server.entity.GrowthLevel;
import com.aischool.server.mapper.GrowthLevelMapper;
import com.aischool.server.service.ai.AiImageClient;
import com.aischool.server.service.asset.AssetService;
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
 * IP 成长画像（批40c 垫图版）：素材库 IP 底图（男小石 ip_shi / 女小萌 ip_meng）+ 学生真实成长关键字
 * → 图像编辑 API（底图+提示词），注入报告「成长画像」页。
 * 关键字三源（与报告同源，非凭空）：九维最强格（buildGrids 同数据）/ 成长等级（buildGrowthSymbol 同数据）/
 * 扬长课程参与最多类别（buildActivities 同数据）；中文→英文画面元素走固定词表（可控可测，非 LLM 生成）。
 * 隐私红线：提示词不含学生姓名/学号等任何个人信息——组合上限 等级5×格9×课程7≈315 张，
 * sha256(底图+提示词+模型) 即缓存键：同一学生教师版/家长版/重新生成全部复用，不重复扣费。
 * 双层缓存：MinIO portrait/{sha}.{ext} 权威（容器重建不丢）+ 本地 cache-dir 物化（渲染子进程直接读）。
 * 降级链：生成失败或 AI 未配置 → 返回 IP 原图（底图物化，报告仍有形象）；底图未上传 → null（PDF 虚线占位框）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortraitService {

    private final AiImageClient aiImageClient;
    private final MinioClient minioClient;
    private final GrowthLevelMapper growthLevelMapper;
    private final AssetService assetService;

    @Value("${aischool.minio.bucket}")
    private String bucket;
    @Value("${aischool.portrait.cache-dir}")
    private String cacheDir;

    /** IP 底图素材位（批40c）：女→小萌，其余（含未填性别）→小石 */
    static final String ASSET_MENG = "ip_meng";
    static final String ASSET_SHI = "ip_shi";

    /**
     * 九格 → 周边画面元素（最强维度）。批36 实测定论：提示词必须英文（中文主体遵循 0/4）。
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

    /** 成长等级 → 角色手持物（symbol_name 可被学校改名，命中不上时不加持物只留周边元素） */
    private static final Map<String, String> SYMBOL_OBJECTS = Map.of(
            "种子", "a small glowing golden seed",
            "嫩芽", "a tiny potted sprout with two fresh green leaves",
            "小树", "a small potted sapling with light-green leaves",
            "大树", "a lush mini tree in a decorated pot",
            "栋梁", "a miniature tall tree with a strong straight trunk");

    /** 扬长课程七类 → 周边元素（参与最多的类别；「其他」= 临时性活动不映射） */
    private static final Map<String, String> COURSE_HINTS = Map.of(
            "德育课程", "a small red volunteer sash and a golden star",
            "扬长选修", "an open sketchbook and colorful pencils",
            "体艺特训队", "a badminton racket and a shiny gold medal",
            "跨学科学习", "stacked books with a small globe and gears",
            "做中学", "handmade wooden crafts and a small robot kit",
            "社会实践", "a red volunteer sash and a small megaphone");

    /** 同 hash 并发只放一个进 API（批渲染 6 并发撞同一张图时不重复扣费） */
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    /**
     * 数据侧入口：growthSymbol/grids/activities 即报告契约同源对象；gender 选底图。
     * 返回本地图片绝对路径；底图未上传返回 null（PDF 保持虚线占位框）。
     */
    public String materialize(String gender, Map<String, Object> growthSymbol,
                              List<Map<String, Object>> grids, List<Map<String, Object>> activities) {
        try {
            // gender 库内口径为 M/F（名册导入链落库），兼容历史中文值
            boolean meng = gender != null && ("F".equalsIgnoreCase(gender.trim()) || "女".equals(gender.trim()));
            String assetKey = meng ? ASSET_MENG : ASSET_SHI;
            byte[] base = assetService.readAssetBytes(assetKey);
            if (base == null) {
                return null; // 底图未上传：虚线占位框（IP 素材不入仓库，须校方在素材库上传）
            }
            String prompt = buildPrompt(growthSymbol, grids, activities);
            String model = aiImageClient.currentConfig().model();
            String hash = sha256(sha256(base) + "|" + prompt + "|" + model); // 底图更换（校方换图）自动失效
            Path local = localFile(hash);
            if (local != null) {
                return local.toAbsolutePath().toString();
            }
            Object lock = keyLocks.computeIfAbsent(hash, k -> new Object());
            synchronized (lock) {
                local = localFile(hash); // 排队等到的再查一次，前一个线程可能已落盘
                if (local == null) {
                    byte[] bytes = fetchBytes(hash, base, prompt);
                    if (bytes == null) {
                        // 生成失败/未配置 → IP 原图占位（报告仍有形象，比空框友好）
                        return materializeBase(assetKey, base);
                    }
                    local = store(hash, bytes);
                }
            }
            return local.toAbsolutePath().toString();
        } catch (Exception e) {
            log.warn("成长画像生成失败（降级为 IP 原图/占位框）: {}", e.getMessage());
            return null;
        }
    }

    /** MinIO 有则下载到本地，无则调 API 生成；失败返回 null（外层降级 IP 原图） */
    private byte[] fetchBytes(String hash, byte[] base, String prompt) {
        if (!aiImageClient.enabled()) {
            return null;
        }
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
        try {
            return aiImageClient.edit(base, prompt);
        } catch (Exception e) {
            log.warn("成长画像改图失败（降级 IP 原图）: {}", e.getMessage());
            return null;
        }
    }

    /** IP 原图兜底：底图物化到 cache-dir（幂等覆盖），返回路径 */
    private String materializeBase(String assetKey, byte[] base) throws Exception {
        String ext = "image/png".equals(AiImageClient.magicOf(base)) ? "png" : "jpg";
        Files.createDirectories(Paths.get(cacheDir));
        Path target = Paths.get(cacheDir).resolve("base-" + assetKey + "." + ext);
        Files.write(target, base);
        return target.toAbsolutePath().toString();
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

    /**
     * 垫图提示词：保持底图角色完全不变（形象/IP 一致性是本方案核心），角色手持等级意象物，
     * 周边加最强素养格+最热扬长课程元素。中文关键词只当查表 key，不进提示词（英文遵循度）。
     */
    private String buildPrompt(Map<String, Object> growthSymbol,
                               List<Map<String, Object>> grids, List<Map<String, Object>> activities) {
        StringBuilder around = new StringBuilder();
        String topGrid = grids == null ? "" : grids.stream()
                .filter(g -> g.get("cur") instanceof Map<?, ?> cur
                        && ((Map<?, ?>) cur).get("mine") instanceof Number)
                .max(Comparator.comparingDouble(g -> ((Number) ((Map<?, ?>) g.get("cur")).get("mine")).doubleValue()))
                .map(g -> String.valueOf(g.get("name")))
                .orElse("");
        if (SCENE_HINTS.containsKey(topGrid)) {
            around.append(SCENE_HINTS.get(topGrid));
        }
        String topCourse = activities == null ? "" : activities.stream()
                .map(a -> String.valueOf(a.getOrDefault("type", "")))
                .filter(t -> COURSE_HINTS.containsKey(t))
                .collect(java.util.stream.Collectors.groupingBy(t -> t, java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
        if (COURSE_HINTS.containsKey(topCourse)) {
            if (around.length() > 0) {
                around.append(" and ");
            }
            around.append(COURSE_HINTS.get(topCourse));
        }
        String symbolObject = "";
        int level = growthSymbol == null || growthSymbol.get("level") == null
                ? -1 : ((Number) growthSymbol.get("level")).intValue();
        if (level >= 0) {
            List<GrowthLevel> levels = growthLevelMapper.selectList(new LambdaQueryWrapper<GrowthLevel>()
                    .orderByAsc(GrowthLevel::getLevel));
            String symbol = levels.stream().filter(l -> l.getLevel() != null && l.getLevel() == level)
                    .findFirst().map(GrowthLevel::getSymbolName).orElse("");
            symbolObject = SYMBOL_OBJECTS.getOrDefault(symbol, "");
        }
        StringBuilder p = new StringBuilder(
                "Keep the cartoon character in the image exactly the same — same face, same pose, same colors,"
                        + " same proportions and same flat art style. Do not redraw or restyle the character.");
        if (!symbolObject.isEmpty()) {
            p.append(" The character now happily holds ").append(symbolObject).append(".");
        }
        if (around.length() > 0) {
            p.append(" Around the character add: ").append(around).append(".");
        }
        p.append(" Keep the plain light background clean and simple."
                + " Absolutely no text, no letters, no numbers, no watermark.");
        return p.toString();
    }

    private static String sha256(byte[] b) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(b);
        return HexFormat.of().formatHex(d);
    }

    private static String sha256(String s) throws Exception {
        return sha256(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
