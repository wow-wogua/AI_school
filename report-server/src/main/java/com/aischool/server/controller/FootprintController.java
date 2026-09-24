package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.TeacherFootprint;
import com.aischool.server.entity.TeacherHonor;
import com.aischool.server.mapper.TeacherFootprintMapper;
import com.aischool.server.mapper.TeacherHonorMapper;
import com.aischool.server.security.AuthUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 教师成长足迹：公开课/听课/讲座/读书笔记/工作室（本人录入）；奖项维度聚合 t_teacher_honor（原始需求三） */
@RestController
@RequestMapping("/api/footprint")
@RequiredArgsConstructor
public class FootprintController {

    private final TeacherFootprintMapper footprintMapper;
    private final TeacherHonorMapper honorMapper;

    @Data
    public static class CreateReq {
        private String type;
        private String title;
        private String footDate;
        private String place;
        private String note;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody CreateReq req) {
        rejectParent();
        if (req.getType() == null || !TeacherFootprint.TYPES.contains(req.getType())) {
            throw new BizException(400, "足迹类型不合法");
        }
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BizException(400, "请填写标题");
        }
        if (req.getTitle().trim().length() > 128) {
            throw new BizException(400, "标题不能超过 128 字");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(req.getFootDate());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BizException(400, "请选择日期");
        }
        if (req.getPlace() != null && req.getPlace().length() > 100) {
            throw new BizException(400, "地点不能超过 100 字");
        }
        if (req.getNote() != null && req.getNote().length() > 500) {
            throw new BizException(400, "备注不能超过 500 字");
        }
        TeacherFootprint f = new TeacherFootprint();
        f.setTeacherId(AuthUtil.current().userId());
        f.setType(req.getType());
        f.setTitle(req.getTitle().trim());
        f.setFootDate(date);
        f.setPlace(req.getPlace());
        f.setNote(req.getNote());
        footprintMapper.insert(f);
        return ApiResponse.ok(Map.of("id", f.getId()));
    }

    /** 我的足迹（foot_date 倒序；type 可选筛选） */
    @GetMapping("/my")
    public ApiResponse<List<TeacherFootprint>> my(@RequestParam(required = false) String type) {
        rejectParent();
        return ApiResponse.ok(footprintMapper.selectList(new LambdaQueryWrapper<TeacherFootprint>()
                .eq(TeacherFootprint::getTeacherId, AuthUtil.current().userId())
                .eq(type != null && !type.isBlank(), TeacherFootprint::getType, type)
                .orderByDesc(TeacherFootprint::getFootDate)
                .orderByDesc(TeacherFootprint::getId)));
    }

    /** 六维统计：五类足迹计数 + 奖项（t_teacher_honor）计数 */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Long>> summary() {
        rejectParent();
        Long uid = AuthUtil.current().userId();
        Map<String, Long> m = new LinkedHashMap<>();
        for (String t : TeacherFootprint.TYPES) {
            m.put(t, footprintMapper.selectCount(new LambdaQueryWrapper<TeacherFootprint>()
                    .eq(TeacherFootprint::getTeacherId, uid)
                    .eq(TeacherFootprint::getType, t)));
        }
        m.put("AWARD", honorMapper.selectCount(new LambdaQueryWrapper<TeacherHonor>()
                .eq(TeacherHonor::getTeacherId, uid)));
        return ApiResponse.ok(m);
    }

    /** 删除本人足迹 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        rejectParent();
        TeacherFootprint f = footprintMapper.selectById(id);
        if (f == null) {
            throw new BizException(404, "足迹记录不存在");
        }
        if (!f.getTeacherId().equals(AuthUtil.current().userId())) {
            throw new BizException(403, "只能删除自己的足迹");
        }
        footprintMapper.deleteById(id);
        return ApiResponse.ok();
    }

    private void rejectParent() {
        if ("PARENT".equals(AuthUtil.current().role())) {
            throw new BizException(403, "家长账号无教师成长足迹");
        }
    }
}
