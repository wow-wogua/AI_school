package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Venue;
import com.aischool.server.mapper.VenueMapper;
import com.aischool.server.security.AuthUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 场地字典（批11）：教师端「行政办公-场地申请」下拉用（仅启用项；管理端 CRUD 在 AdminOaController） */
@RestController
@RequestMapping("/api/venue")
@RequiredArgsConstructor
public class VenueController {

    private final VenueMapper venueMapper;

    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list() {
        if ("PARENT".equals(AuthUtil.current().role())) {
            throw new BizException(403, "家长账号无需申请场地");
        }
        List<Map<String, Object>> out = venueMapper.selectList(new LambdaQueryWrapper<Venue>()
                        .eq(Venue::getStatus, 1).orderByAsc(Venue::getId))
                .stream().<Map<String, Object>>map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", v.getId());
                    m.put("name", v.getName());
                    m.put("location", v.getLocation() == null ? "" : v.getLocation());
                    m.put("capacity", v.getCapacity());
                    return m;
                }).toList();
        return ApiResponse.ok(out);
    }
}
