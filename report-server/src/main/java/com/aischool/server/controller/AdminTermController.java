package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.Exam;
import com.aischool.server.entity.Term;
import com.aischool.server.mapper.ExamMapper;
import com.aischool.server.mapper.TermMapper;
import com.aischool.server.security.AuthUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 管理端：学期管理（仅管理员；is_current 全局单活） */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminTermController {

    private final TermMapper termMapper;
    private final ExamMapper examMapper;

    private void checkAdmin() {
        if (!"ADMIN".equals(AuthUtil.current().role())) {
            throw new BizException(403, "只有管理员可操作系统管理");
        }
    }

    @GetMapping("/term/list")
    public ApiResponse<List<Term>> termList() {
        checkAdmin();
        return ApiResponse.ok(termMapper.selectList(
                new LambdaQueryWrapper<Term>().orderByAsc(Term::getId)));
    }

    @Data
    public static class TermReq {
        @NotBlank(message = "name 不能为空")
        private String name;
        @NotNull(message = "startDate 不能为空")
        private LocalDate startDate;
        @NotNull(message = "endDate 不能为空")
        private LocalDate endDate;
        private Integer isCurrent;
    }

    @PostMapping("/term")
    public ApiResponse<Map<String, Object>> createTerm(@Validated @RequestBody TermReq req) {
        checkAdmin();
        checkRange(req);
        Term t = new Term();
        copy(req, t);
        t.setIsCurrent(0);
        termMapper.insert(t);
        if (Integer.valueOf(1).equals(req.getIsCurrent())) {
            activate(t.getId());
        }
        return ApiResponse.ok(Map.of("termId", t.getId()));
    }

    @PutMapping("/term/{id}")
    public ApiResponse<Void> updateTerm(@PathVariable Long id, @Validated @RequestBody TermReq req) {
        checkAdmin();
        Term t = termMapper.selectById(id);
        if (t == null) {
            throw new BizException(404, "学期不存在");
        }
        checkRange(req);
        // 唯一当前学期不可被取消当前（全库必须恰有一个当前学期）
        if (Integer.valueOf(1).equals(t.getIsCurrent()) && !Integer.valueOf(1).equals(req.getIsCurrent())) {
            throw new BizException(400, "必须保留一个当前学期，请先将其他学期设为当前");
        }
        copy(req, t);
        termMapper.updateById(t);
        if (Integer.valueOf(1).equals(req.getIsCurrent())) {
            activate(id);
        }
        return ApiResponse.ok();
    }

    @DeleteMapping("/term/{id}")
    public ApiResponse<Void> deleteTerm(@PathVariable Long id) {
        checkAdmin();
        Term t = termMapper.selectById(id);
        if (t == null) {
            throw new BizException(404, "学期不存在");
        }
        if (Integer.valueOf(1).equals(t.getIsCurrent())) {
            throw new BizException(400, "当前学期不可删除");
        }
        if (examMapper.selectCount(new LambdaQueryWrapper<Exam>().eq(Exam::getTermId, id)) > 0) {
            throw new BizException(400, "该学期已有考试，不可删除");
        }
        termMapper.deleteById(id);
        return ApiResponse.ok();
    }

    private void checkRange(TermReq req) {
        if (!req.getStartDate().isBefore(req.getEndDate())) {
            throw new BizException(400, "startDate 必须早于 endDate");
        }
    }

    private void copy(TermReq req, Term t) {
        t.setName(req.getName());
        t.setStartDate(req.getStartDate());
        t.setEndDate(req.getEndDate());
        if (req.getIsCurrent() != null) {
            t.setIsCurrent(req.getIsCurrent());
        }
    }

    /** 设为当前：其余学期全部清 0（全局单活） */
    private void activate(Long id) {
        termMapper.update(null, new LambdaUpdateWrapper<Term>()
                .ne(Term::getId, id)
                .eq(Term::getIsCurrent, 1)
                .set(Term::getIsCurrent, 0));
    }
}
