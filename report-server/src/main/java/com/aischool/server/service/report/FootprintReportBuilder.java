package com.aischool.server.service.report;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.ReportTemplate;
import com.aischool.server.entity.TeacherFootprint;
import com.aischool.server.entity.TeacherHonor;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.ReportTemplateMapper;
import com.aischool.server.mapper.TeacherFootprintMapper;
import com.aischool.server.mapper.TeacherHonorMapper;
import com.aischool.server.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 教师成长足迹报告（批26，原始需求三 PDF 化）：六维统计 + 五类足迹明细 + 奖项列表。
 * 同步按需渲染（单人低频导出），不入报告任务队列。
 */
@Service
@RequiredArgsConstructor
public class FootprintReportBuilder {

    public static final Map<String, String> DIM_LABELS = new LinkedHashMap<>() {{
        put("OPEN_CLASS", "公开课");
        put("OBSERVE", "听课");
        put("AWARD", "奖项");
        put("LECTURE", "讲座");
        put("READING", "读书笔记");
        put("STUDIO", "工作室");
    }};

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserMapper userMapper;
    private final TeacherFootprintMapper footprintMapper;
    private final TeacherHonorMapper honorMapper;
    private final ReportTemplateMapper reportTemplateMapper;

    public Map<String, Object> build(Long teacherId) {
        User teacher = userMapper.selectById(teacherId);
        if (teacher == null) {
            throw new BizException(404, "教师不存在: " + teacherId);
        }
        ReportTemplate template = reportTemplateMapper.selectOne(new LambdaQueryWrapper<ReportTemplate>()
                .eq(ReportTemplate::getStatus, "启用").last("LIMIT 1"));
        if (template == null) {
            throw new BizException(500, "未配置启用的报告模板");
        }
        List<TeacherFootprint> records = footprintMapper.selectList(
                new LambdaQueryWrapper<TeacherFootprint>()
                        .eq(TeacherFootprint::getTeacherId, teacherId)
                        .orderByDesc(TeacherFootprint::getFootDate)
                        .orderByDesc(TeacherFootprint::getId));
        List<TeacherHonor> honors = honorMapper.selectList(new LambdaQueryWrapper<TeacherHonor>()
                .eq(TeacherHonor::getTeacherId, teacherId)
                .orderByDesc(TeacherHonor::getHonorDate)
                .orderByDesc(TeacherHonor::getId));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schoolName", template.getSchoolName());
        m.put("generatedAt", LocalDate.now().format(DATE_FMT));
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", teacher.getRealName());
        t.put("username", teacher.getUsername());
        t.put("role", "HEAD_TEACHER".equals(teacher.getRole()) ? "班主任"
                : "TEACHER".equals(teacher.getRole()) ? "教师"
                : "LEADER".equals(teacher.getRole()) ? "领导" : teacher.getRole());
        m.put("teacher", t);

        List<Map<String, Object>> dims = new ArrayList<>();
        for (Map.Entry<String, String> e : DIM_LABELS.entrySet()) {
            String key = e.getKey();
            Map<String, Object> dim = new LinkedHashMap<>();
            dim.put("key", key);
            dim.put("label", e.getValue());
            if ("AWARD".equals(key)) {
                dim.put("count", honors.size());
                dim.put("records", List.of());   // 奖项单独成节（含级别/颁发单位），不混入记录流
            } else {
                List<TeacherFootprint> mine = records.stream()
                        .filter(r -> key.equals(r.getType())).toList();
                dim.put("count", mine.size());
                dim.put("records", mine.stream().map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("title", r.getTitle());
                    row.put("date", r.getFootDate() != null ? r.getFootDate().format(DATE_FMT) : "");
                    row.put("place", r.getPlace() == null ? "" : r.getPlace());
                    row.put("note", r.getNote() == null ? "" : r.getNote());
                    return row;
                }).toList());
            }
            dims.add(dim);
        }
        m.put("dims", dims);
        m.put("honors", honors.stream().map(h -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", h.getName());
            row.put("level", h.getLevel() == null ? "" : h.getLevel());
            row.put("issuer", h.getIssuer() == null ? "" : h.getIssuer());
            row.put("date", h.getHonorDate() != null ? h.getHonorDate().format(DATE_FMT) : "");
            return row;
        }).toList());
        return m;
    }
}
