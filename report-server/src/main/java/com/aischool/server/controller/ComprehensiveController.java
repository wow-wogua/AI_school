package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Comprehensive;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.Term;
import com.aischool.server.mapper.ComprehensiveMapper;
import com.aischool.server.mapper.TermMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.security.UserPrincipal;
import com.aischool.server.service.auth.DataScopeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 综合素质评价（功能点 §11）：五维 A–D 录入即 upsert；final 服务端按「众数并列取高」裁定 */
@RestController
@RequestMapping("/api/comprehensive")
@RequiredArgsConstructor
public class ComprehensiveController {

    private static final List<String> LEVELS = List.of("A", "B", "C", "D");

    private final ComprehensiveMapper comprehensiveMapper;
    private final TermMapper termMapper;
    private final DataScopeService dataScope;

    @Data
    public static class SaveReq {
        @NotNull(message = "studentId 不能为空")
        private Long studentId;
        @NotNull(message = "termId 不能为空")
        private Long termId;
        private String moral;      // 思想品德
        private String ability;    // 学业水平
        private String health;     // 身心健康
        private String aesthetic;  // 艺术素养
        private String practice;   // 社会实践
    }

    /** 查询（数据可见即可读） */
    @GetMapping
    public ApiResponse<Map<String, Object>> get(@RequestParam Long studentId, @RequestParam Long termId) {
        dataScope.checkStudentAccess(AuthUtil.current(), studentId);
        return ApiResponse.ok(view(select(studentId, termId)));
    }

    /** 录入/修改（ADMIN 或本班班主任，与活动/荣誉同口径） */
    @PutMapping
    public ApiResponse<Map<String, Object>> save(@Validated @RequestBody SaveReq req) {
        UserPrincipal user = AuthUtil.current();
        Student student = dataScope.checkStudentAccess(user, req.studentId);
        if (!"ADMIN".equals(user.role())) {
            dataScope.checkClassOperable(user, student.getClassId());
        }
        if (termMapper.selectById(req.termId) == null) {
            throw new BizException(404, "学期不存在");
        }
        String moral = level(req.getMoral()), ability = level(req.getAbility()),
                health = level(req.getHealth()), aesthetic = level(req.getAesthetic()),
                practice = level(req.getPractice());

        Comprehensive c = select(req.studentId, req.termId);
        boolean created = c == null;
        if (created) {
            c = new Comprehensive();
            c.setStudentId(req.studentId);
            c.setTermId(req.termId);
        }
        c.setMoral(moral);
        c.setAbility(ability);
        c.setHealth(health);
        c.setAesthetic(aesthetic);
        c.setPractice(practice);
        c.setFinalLevel(finalOf(moral, ability, health, aesthetic, practice));
        if (created) {
            comprehensiveMapper.insert(c);
        } else {
            comprehensiveMapper.updateById(c);
        }
        return ApiResponse.ok(view(c));
    }

    // ───────────────── 内部 ─────────────────

    /** 维度可空（未评），非空必须 A–D */
    private String level(String v) {
        if (v == null || v.isBlank()) {
            return "";
        }
        String t = v.trim();
        if (!LEVELS.contains(t)) {
            throw new BizException(400, "等级只能是 A/B/C/D：" + v);
        }
        return t;
    }

    /** 众数并列取高：计数最多者；并列取字母序小者（A>B>C>D）。全空返回 "" */
    static String finalOf(String... dims) {
        String best = null;
        int bestCount = 0;
        for (String lv : LEVELS) {
            int n = 0;
            for (String d : dims) {
                if (lv.equals(d)) {
                    n++;
                }
            }
            if (n > bestCount) {
                best = lv;
                bestCount = n;
            }
        }
        return best == null ? "" : best;
    }

    private Comprehensive select(Long studentId, Long termId) {
        return comprehensiveMapper.selectOne(new LambdaQueryWrapper<Comprehensive>()
                .eq(Comprehensive::getStudentId, studentId)
                .eq(Comprehensive::getTermId, termId).last("LIMIT 1"));
    }

    private Map<String, Object> view(Comprehensive c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("studentId", c == null ? null : c.getStudentId());
        m.put("termId", c == null ? null : c.getTermId());
        m.put("moral", c == null ? "" : c.getMoral());
        m.put("ability", c == null ? "" : c.getAbility());
        m.put("health", c == null ? "" : c.getHealth());
        m.put("aesthetic", c == null ? "" : c.getAesthetic());
        m.put("practice", c == null ? "" : c.getPractice());
        m.put("finalLevel", c == null ? "" : c.getFinalLevel());
        return m;
    }
}
