package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.entity.TeacherFootprint;
import com.aischool.server.entity.TeacherHonor;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.TeacherFootprintMapper;
import com.aischool.server.mapper.TeacherHonorMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.service.auth.PermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 管理端教师足迹：全校六维总览+列表筛选（ADMIN/有 ADMIN_ACCESS 的领导） */
@RestController
@RequestMapping("/api/admin/footprint")
@RequiredArgsConstructor
public class AdminFootprintController {

    private final TeacherFootprintMapper footprintMapper;
    private final TeacherHonorMapper honorMapper;
    private final UserMapper userMapper;
    private final PermissionService permission;

    /** 全校列表（teacherId/type 可选筛选，foot_date 倒序） */
    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) Long teacherId,
                                                       @RequestParam(required = false) String type) {
        permission.checkAdminAccess();
        List<TeacherFootprint> rows = footprintMapper.selectList(new LambdaQueryWrapper<TeacherFootprint>()
                .eq(teacherId != null, TeacherFootprint::getTeacherId, teacherId)
                .eq(type != null && !type.isBlank(), TeacherFootprint::getType, type)
                .orderByDesc(TeacherFootprint::getFootDate)
                .orderByDesc(TeacherFootprint::getId)
                .last("LIMIT 500"));
        Map<Long, User> users = rows.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(rows.stream().map(TeacherFootprint::getTeacherId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<Map<String, Object>> out = rows.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", f.getId());
            m.put("type", f.getType());
            m.put("title", f.getTitle());
            m.put("footDate", f.getFootDate());
            m.put("place", f.getPlace());
            m.put("note", f.getNote());
            m.put("createTime", f.getCreateTime());
            User u = users.get(f.getTeacherId());
            m.put("teacherName", u == null ? "" : u.getRealName());
            return m;
        }).collect(Collectors.toList());
        return ApiResponse.ok(out);
    }

    /** 全校六维总览 */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Long>> summary() {
        permission.checkAdminAccess();
        Map<String, Long> m = new LinkedHashMap<>();
        for (String t : TeacherFootprint.TYPES) {
            m.put(t, footprintMapper.selectCount(new LambdaQueryWrapper<TeacherFootprint>()
                    .eq(TeacherFootprint::getType, t)));
        }
        m.put("AWARD", honorMapper.selectCount(null));
        return ApiResponse.ok(m);
    }
}
