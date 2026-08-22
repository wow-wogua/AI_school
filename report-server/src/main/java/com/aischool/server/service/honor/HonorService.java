package com.aischool.server.service.honor;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.Honor;
import com.aischool.server.mapper.HonorMapper;
import com.aischool.server.service.ai.AiClient;
import com.aischool.server.service.coin.CoinLedgerService;
import com.aischool.server.service.report.PdfStoreService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 荣誉与证书：上传 →（AI 视觉识别 | 手动填写）→ 教师确认生效（可选能量币入账） */
@Slf4j
@Service
@RequiredArgsConstructor
public class HonorService {

    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HonorMapper honorMapper;
    private final PdfStoreService pdfStore;
    private final AiClient aiClient;
    private final CoinLedgerService coinLedger;
    private final ObjectMapper om = new ObjectMapper();

    /**
     * 上传证书：存 MinIO → AI 识别（未配置/失败/PDF 一律降级手动）→ 落库待确认。
     *
     * @return {honorId, fileUrl, source: ai|manual, detail, parsed}
     */
    public Map<String, Object> upload(Long studentId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请选择证书文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(400, "证书文件不能超过 10MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        boolean isImage = ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png");
        if (!isImage && !ext.equals("pdf")) {
            throw new BizException(400, "仅支持 jpg/jpeg/png/pdf 格式");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BizException(400, "读取上传文件失败");
        }
        String objectName = "honor/" + studentId + "/" + UUID.randomUUID() + "." + ext;
        pdfStore.upload(objectName, new ByteArrayInputStream(bytes), bytes.length, file.getContentType());

        Map<String, String> parsed = null;
        String source = "manual";
        String detail;
        if (!aiClient.enabled()) {
            detail = "AI 未配置，请手动填写";
        } else if (!isImage) {
            detail = "PDF 证书不支持视觉识别，请手动填写";
        } else {
            try {
                String dataUrl = "data:" + (file.getContentType() == null ? "image/jpeg" : file.getContentType())
                        + ";base64," + Base64.getEncoder().encodeToString(bytes);
                String raw = aiClient.chatVision(
                        "你是证书信息识别助手，只输出 JSON，不输出任何其他文字。",
                        "识别这张荣誉证书，输出 JSON：{\"name\":\"奖项名称\",\"level\":\"国家级/省级/市级/区级/校级/班级，无法判断填空串\","
                                + "\"issuer\":\"主办单位\",\"date\":\"yyyy-MM-dd，无法判断填空串\"}",
                        dataUrl);
                parsed = parseRecognized(raw);
                source = "ai";
                detail = "AI 识别完成，请核对后确认";
            } catch (Exception e) {
                log.warn("证书 AI 识别失败，降级手动: {}", e.getMessage());
                detail = "AI 识别失败，请手动填写";
            }
        }

        Honor h = new Honor();
        h.setStudentId(studentId);
        h.setName(parsed == null ? "" : parsed.getOrDefault("name", ""));
        h.setLevel(parsed == null ? null : parsed.get("level"));
        h.setIssuer(parsed == null ? null : parsed.get("issuer"));
        h.setHonorDate(parsed == null ? null : parseDate(parsed.get("date")));
        h.setFileUrl(objectName);
        h.setAiParsed(toJsonOrNull(parsed));
        h.setConfirmStatus("待确认");
        honorMapper.insert(h);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("honorId", h.getId());
        data.put("fileUrl", objectName);
        data.put("source", source);
        data.put("detail", detail);
        data.put("parsed", parsed);
        return data;
    }

    /** 待确认态编辑字段 */
    public void save(Long id, String name, String level, String issuer, LocalDate honorDate) {
        Honor h = requirePending(id);
        honorMapper.update(null, new LambdaUpdateWrapper<Honor>()
                .eq(Honor::getId, id)
                .set(Honor::getName, name == null ? "" : name)
                .set(Honor::getLevel, level)
                .set(Honor::getIssuer, issuer)
                .set(Honor::getHonorDate, honorDate));
    }

    /** 确认生效；coin>0 时入账能量币（荣誉日期为空则落当前学期） */
    public Map<String, Object> confirm(Long id, BigDecimal coin) {
        Honor h = requirePending(id);
        if (h.getName() == null || h.getName().isBlank()) {
            throw new BizException(400, "请先填写奖项名称再确认");
        }
        honorMapper.update(null, new LambdaUpdateWrapper<Honor>()
                .eq(Honor::getId, id).set(Honor::getConfirmStatus, "已确认"));
        Long termId = null;
        if (coin != null && coin.compareTo(BigDecimal.ZERO) > 0) {
            termId = coinLedger.income(h.getStudentId(), h.getHonorDate(), "荣誉", id,
                    "荣誉-" + h.getName(), coin);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("termId", termId);
        return result;
    }

    /** 仅待确认可删（已确认的关联币账，不可删）；连带删 MinIO 对象 */
    public void delete(Long id) {
        Honor h = requirePending(id);
        honorMapper.deleteById(id);
        if (h.getFileUrl() != null) {
            pdfStore.delete(h.getFileUrl());
        }
    }

    private Honor requirePending(Long id) {
        Honor h = honorMapper.selectById(id);
        if (h == null) {
            throw new BizException(404, "荣誉记录不存在");
        }
        if ("已确认".equals(h.getConfirmStatus())) {
            throw new BizException(400, "该荣誉已确认生效，不可修改或删除");
        }
        return h;
    }

    /** 剥 ```json 围栏后解析 {name,level,issuer,date}；date 空串归 null */
    private Map<String, String> parseRecognized(String raw) {
        String text = raw.trim();
        int fence = text.indexOf("```");
        if (fence >= 0) {
            text = text.substring(text.indexOf('\n', fence) + 1);
            int end = text.lastIndexOf("```");
            if (end >= 0) {
                text = text.substring(0, end);
            }
        }
        int l = text.indexOf('{'), r = text.lastIndexOf('}');
        if (l < 0 || r <= l) {
            throw new BizException(502, "AI 返回不是 JSON");
        }
        try {
            var node = om.readTree(text.substring(l, r + 1));
            Map<String, String> parsed = new LinkedHashMap<>();
            for (String key : new String[]{"name", "level", "issuer", "date"}) {
                String v = node.path(key).asText(null);
                if (v != null) {
                    v = v.trim();
                    parsed.put(key, v.isEmpty() ? null : v);
                }
            }
            return parsed;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "AI 返回解析失败");
        }
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s, DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private String toJsonOrNull(Map<String, String> parsed) {
        if (parsed == null) {
            return null;
        }
        try {
            return om.writeValueAsString(parsed);
        } catch (Exception e) {
            return null;
        }
    }
}
