package com.aischool.server.service.repair;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.Repair;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.RepairMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.UserPrincipal;
import com.aischool.server.service.report.PdfStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 报修单（批10）：文字描述+拍照凭证（≤3 张，MinIO repair/ 前缀，同微光照片模式）。
 * 工单模型不走审批流：提交 PENDING → 管理端处理 DONE/REJECTED。
 */
@Service
@RequiredArgsConstructor
public class RepairService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long MAX_SIZE = 10L * 1024 * 1024;

    private final RepairMapper repairMapper;
    private final UserMapper userMapper;
    private final PdfStoreService pdfStore;

    public Map<String, Object> create(UserPrincipal user, String location, String description,
                                      List<MultipartFile> photos) {
        if (location == null || location.isBlank()) {
            throw new BizException(400, "请填写故障地点或设施");
        }
        if (description == null || description.isBlank()) {
            throw new BizException(400, "请填写故障描述");
        }
        if (description.length() > 500) {
            throw new BizException(400, "故障描述不能超过 500 字");
        }
        List<String> objects = new ArrayList<>();
        if (photos != null) {
            if (photos.size() > 3) {
                throw new BizException(400, "凭证照片最多 3 张");
            }
            for (MultipartFile p : photos) {
                if (p == null || p.isEmpty()) {
                    continue;
                }
                if (p.getSize() > MAX_SIZE) {
                    throw new BizException(400, "照片不能超过 10MB");
                }
                String original = p.getOriginalFilename() == null ? "" : p.getOriginalFilename();
                String ext = original.contains(".")
                        ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
                if (!ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("png")) {
                    throw new BizException(400, "仅支持 jpg/jpeg/png 格式");
                }
                byte[] bytes;
                try {
                    bytes = p.getBytes();
                } catch (Exception e) {
                    throw new BizException(400, "读取照片失败");
                }
                String objectName = "repair/" + UUID.randomUUID() + "." + ext;
                pdfStore.upload(objectName, new ByteArrayInputStream(bytes), bytes.length, p.getContentType());
                objects.add(objectName);
            }
        }
        Repair r = new Repair();
        r.setReporterId(user.userId());
        r.setLocation(location.trim());
        r.setDescription(description.trim());
        r.setPhotos(objects.isEmpty() ? null : toJson(objects));
        r.setStatus(Repair.PENDING);
        repairMapper.insert(r);
        return Map.of("repairId", r.getId());
    }

    /** 我的报修 */
    public List<Map<String, Object>> my(UserPrincipal user, String status) {
        return toRows(repairMapper.selectList(new LambdaQueryWrapper<Repair>()
                .eq(Repair::getReporterId, user.userId())
                .eq(status != null && !status.isBlank(), Repair::getStatus, status)
                .orderByDesc(Repair::getId)));
    }

    /** 管理端全量 */
    public List<Map<String, Object>> list(String status) {
        return toRows(repairMapper.selectList(new LambdaQueryWrapper<Repair>()
                .eq(status != null && !status.isBlank(), Repair::getStatus, status)
                .orderByDesc(Repair::getId)));
    }

    /** 详情（报修人本人或管理员） */
    public Map<String, Object> detail(Long id, UserPrincipal user) {
        Repair r = require(id);
        checkVisible(r, user);
        Map<Long, String> names = userNames(java.util.stream.Stream.of(r.getReporterId(), r.getHandlerId())
                .filter(java.util.Objects::nonNull).distinct().toList()); // handlerId 未处理时为 null，List.of 会 NPE
        Map<String, Object> m = rowOf(r, names);
        m.put("description", r.getDescription());
        m.put("handleNote", r.getHandleNote() == null ? "" : r.getHandleNote());
        m.put("handlerName", r.getHandlerId() == null ? "" : names.getOrDefault(r.getHandlerId(), ""));
        m.put("handleTime", r.getHandleTime());
        return m;
    }

    /** 处理：PENDING → DONE/REJECTED */
    public void handle(Long id, UserPrincipal admin, String status, String note) {
        Repair r = require(id);
        if (!Repair.PENDING.equals(r.getStatus())) {
            throw new BizException(400, "该报修单已处理结束");
        }
        if (!Repair.DONE.equals(status) && !Repair.REJECTED.equals(status)) {
            throw new BizException(400, "处理动作须为 DONE（已完成）或 REJECTED（不予受理）");
        }
        if (note != null && note.length() > 300) {
            throw new BizException(400, "处理说明不能超过 300 字");
        }
        r.setStatus(status);
        r.setHandlerId(admin.userId());
        r.setHandleNote(note);
        r.setHandleTime(LocalDateTime.now());
        repairMapper.updateById(r);
    }

    /** 逐张凭证照片回图（照片内容=故障现场，报修人本人与管理员可见） */
    public String photoObject(Long id, int idx, UserPrincipal user) {
        Repair r = require(id);
        checkVisible(r, user);
        List<String> objects = parsePhotos(r.getPhotos());
        if (idx < 0 || idx >= objects.size()) {
            throw new BizException(404, "照片不存在");
        }
        return objects.get(idx);
    }

    private void checkVisible(Repair r, UserPrincipal user) {
        if (!"ADMIN".equals(user.role()) && !r.getReporterId().equals(user.userId())) {
            throw new BizException(403, "仅报修人与管理员可查看该报修单");
        }
    }

    Repair require(Long id) {
        Repair r = repairMapper.selectById(id);
        if (r == null) {
            throw new BizException(404, "报修单不存在");
        }
        return r;
    }

    private List<Map<String, Object>> toRows(List<Repair> rows) {
        Map<Long, String> names = userNames(rows.stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getReporterId(), r.getHandlerId()))
                .filter(java.util.Objects::nonNull).distinct().toList());
        return rows.stream().map(r -> rowOf(r, names)).collect(Collectors.toList());
    }

    private Map<String, Object> rowOf(Repair r, Map<Long, String> names) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("location", r.getLocation());
        m.put("description", r.getDescription());
        m.put("status", r.getStatus());
        m.put("reporterName", names.getOrDefault(r.getReporterId(), ""));
        m.put("handlerName", r.getHandlerId() == null ? "" : names.getOrDefault(r.getHandlerId(), ""));
        m.put("handleNote", r.getHandleNote() == null ? "" : r.getHandleNote());
        m.put("handleTime", r.getHandleTime());
        m.put("createTime", r.getCreateTime());
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < parsePhotos(r.getPhotos()).size(); i++) {
            urls.add("/api/repair/file/" + r.getId() + "?idx=" + i); // 前端 fetchBlob 直取（同封面/微光照片模式）
        }
        m.put("photoCount", urls.size());
        m.put("photoUrls", urls);
        return m;
    }

    private Map<Long, String> userNames(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        return distinct.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(distinct).stream()
                        .collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));
    }

    private List<String> parsePhotos(String photos) {
        if (photos == null || photos.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(photos, JSON.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (Exception e) {
            throw new BizException(500, "照片清单序列化失败");
        }
    }
}
