package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.ParentBinding;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.ParentBindingMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.auth.DataScopeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aischool.server.mapper.StudentMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 学生选择（发起报告生成用）：按数据权限过滤；仅返回在读（转出/毕业学生不参与日常业务） */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    /** 批8 学号规则：标识(小1/初2)+入学年2位+班号2位+编号2位 */
    private static final Pattern STUDENT_NO = Pattern.compile("^[12][0-9]{6}$");

    private final StudentMapper studentMapper;
    private final DataScopeService dataScopeService;
    private final ParentBindingMapper bindingMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        var user = AuthUtil.current();
        List<Long> visible = dataScopeService.visibleClassIds(user);

        LambdaQueryWrapper<Student> qw = new LambdaQueryWrapper<Student>()
                .eq(Student::getStatus, "在读")
                .eq(classId != null, Student::getClassId, classId)
                .like(keyword != null && !keyword.isBlank(), Student::getName, keyword)
                // 空列表须转哨兵：MyBatis-Plus .in(空集合) 生成 IN () 非法 SQL（无班教师 500）
                .in(visible != null, Student::getClassId, visible == null || visible.isEmpty() ? List.of(-1L) : visible)
                .orderByAsc(Student::getStudentNo);
        Page<Student> p = studentMapper.selectPage(Page.of(page, Math.min(size, 100)), qw);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", p.getTotal());
        m.put("records", p.getRecords().stream().map(s -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", s.getId());
            r.put("studentNo", s.getStudentNo());
            r.put("name", s.getName());
            r.put("gender", s.getGender());
            r.put("classId", s.getClassId());
            r.put("status", s.getStatus());
            return r;
        }).toList());
        return ApiResponse.ok(m);
    }

    /** 单个学生基本信息（App 学生详情页用；数据权限校验同上） */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        var user = AuthUtil.current();
        Student s = dataScopeService.checkStudentAccess(user, id);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", s.getId());
        r.put("studentNo", s.getStudentNo());
        r.put("name", s.getName());
        r.put("gender", s.getGender());
        r.put("classId", s.getClassId());
        r.put("status", s.getStatus());
        r.put("guardianName", s.getGuardianName());
        r.put("guardianPhone", s.getGuardianPhone());
        r.put("dormBuilding", s.getDormBuilding());
        r.put("dormRoom", s.getDormRoom());
        r.put("dormBed", s.getDormBed());
        // 管理员/领导/该班班主任可在 App 端直接订正资料（批8.5）；任课教师只读
        boolean editable;
        try {
            dataScopeService.checkClassOperable(user, s.getClassId());
            editable = true;
        } catch (BizException e) {
            editable = false;
        }
        r.put("editable", editable);
        return ApiResponse.ok(r);
    }

    // ────────────────── 批8.5：班主任/级长/管理员订正学生资料 ──────────────────

    @Data
    public static class ProfileReq {
        private String name;          // 非空才生效
        private String gender;        // M/F；空串=清空
        private String studentNo;     // 非空且须符合规则、全校唯一
        private String guardianName;  // 空串=清空
        private String guardianPhone; // 空串=清空
        private String dormBuilding;  // 空串=清空
        private String dormRoom;
        private String dormBed;
    }

    /** 订正学生基本资料（转班/转出/删除仍走管理端；学号/宿舍/家长联系等日常维护在此） */
    @PutMapping("/{id}/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@PathVariable Long id, @RequestBody ProfileReq req) {
        var user = AuthUtil.current();
        Student s = dataScopeService.checkStudentAccess(user, id);
        dataScopeService.checkClassOperable(user, s.getClassId());
        if (req.getName() != null && !req.getName().isBlank()) {
            s.setName(req.getName().trim());
        }
        if (req.getGender() != null) {
            String g = req.getGender().isBlank() ? null : req.getGender();
            if (g != null && !"M".equals(g) && !"F".equals(g)) {
                throw new BizException(400, "性别只能是 M/F");
            }
            s.setGender(g);
        }
        if (req.getStudentNo() != null && !req.getStudentNo().isBlank()) {
            String no = req.getStudentNo().trim();
            if (!STUDENT_NO.matcher(no).matches()) {
                throw new BizException(400, "学号须为 7 位：标识(1/2)+入学年+班号+编号，如 1250101");
            }
            if (studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                    .eq(Student::getStudentNo, no).ne(Student::getId, id)) > 0) {
                throw new BizException(400, "学号已存在（全校唯一）");
            }
            s.setStudentNo(no);
        }
        // 可空字段：请求未携带（null）=不动，空串=清空——显式 set（批3 教训：updateById 忽略 null）
        LambdaUpdateWrapper<Student> uw = new LambdaUpdateWrapper<Student>().eq(Student::getId, id)
                .set(Student::getName, s.getName())
                .set(Student::getGender, s.getGender())
                .set(Student::getStudentNo, s.getStudentNo())
                .set(req.getGuardianName() != null, Student::getGuardianName, blankToNull(req.getGuardianName()))
                .set(req.getGuardianPhone() != null, Student::getGuardianPhone, blankToNull(req.getGuardianPhone()))
                .set(req.getDormBuilding() != null, Student::getDormBuilding, blankToNull(req.getDormBuilding()))
                .set(req.getDormRoom() != null, Student::getDormRoom, blankToNull(req.getDormRoom()))
                .set(req.getDormBed() != null, Student::getDormBed, blankToNull(req.getDormBed()));
        studentMapper.update(null, uw);
        return ApiResponse.ok(Map.of("updated", true));
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    // ────────────────── 批8.5：学生详情-家长账号（班主任可见+重置） ──────────────────

    /** 该生已绑定的家长账号（仅管理员/领导/该班班主任；任课教师与家长 403） */
    @GetMapping("/{id}/parents")
    public ApiResponse<List<Map<String, Object>>> parents(@PathVariable Long id) {
        var user = AuthUtil.current();
        Student s = dataScopeService.checkStudentAccess(user, id);
        dataScopeService.checkClassOperable(user, s.getClassId());
        List<ParentBinding> rows = bindingMapper.selectList(new LambdaQueryWrapper<ParentBinding>()
                .eq(ParentBinding::getStudentId, id).orderByAsc(ParentBinding::getId));
        return ApiResponse.ok(rows.stream().map(b -> {
            User u = userMapper.selectById(b.getParentUserId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("parentId", b.getParentUserId());
            m.put("relation", b.getRelation());
            m.put("account", u == null ? "(已删除)" : u.getUsername());
            m.put("realName", u == null ? null : u.getRealName());
            m.put("status", u == null ? null : u.getStatus());
            return m;
        }).toList());
    }

    /** 班主任重置本班家长密码（批8.5）：统一初始密码+首登强制改密，口令一次性回传告知家长 */
    @PostMapping("/{id}/parent/{parentId}/reset-password")
    public ApiResponse<Map<String, Object>> resetParentPassword(@PathVariable Long id, @PathVariable Long parentId) {
        var user = AuthUtil.current();
        Student s = dataScopeService.checkStudentAccess(user, id);
        dataScopeService.checkClassOperable(user, s.getClassId());
        if (bindingMapper.selectCount(new LambdaQueryWrapper<ParentBinding>()
                .eq(ParentBinding::getStudentId, id)
                .eq(ParentBinding::getParentUserId, parentId)) == 0) {
            throw new BizException(404, "该家长未绑定此学生");
        }
        User parent = userMapper.selectById(parentId);
        if (parent == null || !"PARENT".equals(parent.getRole())) {
            throw new BizException(404, "家长账号不存在");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, parentId)
                .set(User::getPasswordHash, passwordEncoder.encode(AdminUserController.INITIAL_PASSWORD))
                .set(User::getMustChangePwd, 1));
        return ApiResponse.ok(Map.of("initialPassword", AdminUserController.INITIAL_PASSWORD));
    }
}
