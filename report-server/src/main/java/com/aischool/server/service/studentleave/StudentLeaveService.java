package com.aischool.server.service.studentleave;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.ParentBinding;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.StudentLeave;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.ParentBindingMapper;
import com.aischool.server.mapper.StudentLeaveMapper;
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.UserPrincipal;
import com.aischool.server.service.auth.DataScopeService;
import com.aischool.server.service.notify.NotificationService;
import com.aischool.server.service.report.PdfStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 学生请假（批27）：家长替孩子提交（照片凭证同报修模式），单级审批——本班班主任
 * 批准/驳回即生效，领导/管理员可代批兜底（验收反馈收紧：任课教师只读不可批，批31）；
 * 门卫对已批准单登记离校/返校时间。独立于 OA 引擎（那是教职工口径）。
 */
@Service
@RequiredArgsConstructor
public class StudentLeaveService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long MAX_SIZE = 10L * 1024 * 1024;

    private final StudentLeaveMapper leaveMapper;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final UserMapper userMapper;
    private final ParentBindingMapper bindingMapper;
    private final DataScopeService dataScope;
    private final PdfStoreService pdfStore;
    private final NotificationService notificationService;

    // ────────────────────────── 家长侧 ──────────────────────────

    /** 家长替绑定孩子提交请假单（类型白名单+起止校验+照片≤3） */
    public Map<String, Object> create(UserPrincipal parent, Long studentId, String leaveType,
                                      LocalDate start, LocalDate end, String reason,
                                      List<MultipartFile> photos) {
        requireParentBound(parent, studentId);
        if (!StudentLeave.TYPES.contains(leaveType)) {
            throw new BizException(400, "请假类型只能是 病假/事假/其他");
        }
        if (start == null || end == null) {
            throw new BizException(400, "请选择请假起止日期");
        }
        if (end.isBefore(start)) {
            throw new BizException(400, "结束日期不能早于开始日期");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(400, "请填写请假事由");
        }
        if (reason.length() > 300) {
            throw new BizException(400, "请假事由不能超过 300 字");
        }
        List<String> objects = uploadPhotos(photos);
        StudentLeave l = new StudentLeave();
        l.setStudentId(studentId);
        l.setParentId(parent.userId());
        l.setLeaveType(leaveType);
        l.setStartDate(start);
        l.setEndDate(end);
        l.setReason(reason.trim());
        l.setPhotos(objects.isEmpty() ? null : toJson(objects));
        l.setStatus(StudentLeave.PENDING);
        leaveMapper.insert(l);
        notifyLeaveTodo(l);
        return Map.of("leaveId", l.getId());
    }

    /** 家长自己的提交记录 */
    public List<Map<String, Object>> my(UserPrincipal parent) {
        List<StudentLeave> rows = leaveMapper.selectList(new LambdaQueryWrapper<StudentLeave>()
                .eq(StudentLeave::getParentId, parent.userId())
                .orderByDesc(StudentLeave::getId));
        return toRows(rows);
    }

    /** 家长撤回待审批的请假单（已批准的须联系老师/管理员处理） */
    public void cancel(Long id, UserPrincipal parent) {
        StudentLeave l = require(id);
        if (!l.getParentId().equals(parent.userId())) {
            throw new BizException(403, "仅提交家长本人可撤回");
        }
        if (!StudentLeave.PENDING.equals(l.getStatus())) {
            throw new BizException(400, "该请假单已审批结束，不能撤回");
        }
        l.setStatus(StudentLeave.CANCELLED);
        leaveMapper.updateById(l);
    }

    // ────────────────────────── 教师侧（含领导/管理员） ──────────────────────────

    /**
     * 教师端列表：status 可筛；scope=my=本班（任课+班主任），scope=all=全校——
     * 仅领导/管理员可用（批31 收紧：教师侧隐藏全校视图）。行内 canApprove 标记
     * 审批权（本班班主任=true，任课教师=false，领导/管理员恒 true）。
     */
    public List<Map<String, Object>> list(UserPrincipal user, String status, String scope) {
        String role = user.role();
        if ("all".equals(scope) && !"LEADER".equals(role) && !"ADMIN".equals(role)) {
            throw new BizException(403, "全校视图仅领导/管理员可用");
        }
        boolean all = "all".equals(scope);
        List<Long> visible = null;
        if (!all) {
            visible = dataScope.visibleClassIds(user); // ADMIN/LEADER 返回 null=全量
        }
        List<StudentLeave> rows = leaveMapper.selectList(new LambdaQueryWrapper<StudentLeave>()
                .eq(status != null && !status.isBlank(), StudentLeave::getStatus, status)
                .orderByDesc(StudentLeave::getId)
                .last("LIMIT 500"));
        if (visible != null && !visible.isEmpty()) {
            rows = filterByClass(rows, visible);
        } else if (visible != null) { // 无任课无带班教师：本班口径下为空
            rows = List.of();
        }
        List<Map<String, Object>> list = toRows(rows);
        // 批31：任课教师只读——行级审批权标记（班主任对本班、领导/管理员全量）
        Map<Long, Student> students = studentsOf(rows);
        Map<Long, Long> classHead = classHeadOf(students.values().stream().map(Student::getClassId).toList());
        for (Map<String, Object> m : list) {
            Student s = students.get(((Number) m.get("studentId")).longValue());
            boolean can;
            if ("LEADER".equals(role) || "ADMIN".equals(role)) {
                can = true;
            } else if ("HEAD_TEACHER".equals(role) && s != null && s.getClassId() != null) {
                can = user.userId().equals(classHead.get(s.getClassId()));
            } else {
                can = false;
            }
            m.put("canApprove", can);
        }
        return list;
    }

    /** 批准（单级：本班班主任；领导/管理员可代批兜底——批31 收紧，任课教师不可批） */
    public void approve(Long id, UserPrincipal user, String note) {
        StudentLeave l = requirePending(id);
        requireApprover(user, l);
        if (note != null && note.length() > 200) {
            throw new BizException(400, "审批意见不能超过 200 字");
        }
        l.setStatus(StudentLeave.APPROVED);
        l.setApproverId(user.userId());
        l.setApproveNote(note == null || note.isBlank() ? null : note.trim());
        l.setApproveTime(LocalDateTime.now());
        leaveMapper.updateById(l);
        // 批29：审批结果通知提交家长
        notificationService.leaveResult(l.getParentId(), studentNameOf(l), true, user.realName(), l.getApproveNote());
    }

    /** 驳回（意见必填——家长须知道原因；权限同批准） */
    public void reject(Long id, UserPrincipal user, String note) {
        StudentLeave l = requirePending(id);
        requireApprover(user, l);
        if (note == null || note.isBlank()) {
            throw new BizException(400, "驳回时请填写原因");
        }
        if (note.length() > 200) {
            throw new BizException(400, "审批意见不能超过 200 字");
        }
        l.setStatus(StudentLeave.REJECTED);
        l.setApproverId(user.userId());
        l.setApproveNote(note.trim());
        l.setApproveTime(LocalDateTime.now());
        leaveMapper.updateById(l);
        notificationService.leaveResult(l.getParentId(), studentNameOf(l), false, user.realName(), l.getApproveNote());
    }

    /** 批29/批31：提交后通知本班班主任（审批人收紧后任课教师不再推送；通知失败不阻断提交） */
    private void notifyLeaveTodo(StudentLeave l) {
        try {
            Student s = studentMapper.selectById(l.getStudentId());
            if (s == null || s.getClassId() == null) {
                return;
            }
            Clazz c = clazzMapper.selectById(s.getClassId());
            if (c == null || c.getHeadTeacherId() == null) {
                return;
            }
            notificationService.leaveTodo(List.of(c.getHeadTeacherId()), s.getName(),
                    c.getName(), l.getLeaveType(),
                    l.getStartDate().toString(), l.getEndDate().toString());
        } catch (Exception e) {
            // 通知属旁路：查不到班级/班主任也不影响请假单已落库
        }
    }

    private String studentNameOf(StudentLeave l) {
        Student s = studentMapper.selectById(l.getStudentId());
        return s == null ? "(学生已删除)" : s.getName();
    }

    // ────────────────────────── 门卫侧 ──────────────────────────

    /** 门卫核验视图：某日有效的已批准请假单（start≤date≤end），可按学号/姓名搜 */
    public List<Map<String, Object>> guardList(UserPrincipal guard, LocalDate date, String q) {
        requireGuard(guard);
        LocalDate d = date == null ? LocalDate.now() : date;
        List<StudentLeave> rows = leaveMapper.selectList(new LambdaQueryWrapper<StudentLeave>()
                .eq(StudentLeave::getStatus, StudentLeave.APPROVED)
                .le(StudentLeave::getStartDate, d)
                .ge(StudentLeave::getEndDate, d)
                .orderByDesc(StudentLeave::getId)
                .last("LIMIT 500"));
        return toRows(rows).stream()
                .filter(m -> matchesKeyword(m, q))
                .collect(Collectors.toList());
    }

    /** 门卫登记离校（幂等口径：已登记则 400，避免覆盖首次时间） */
    public void registerLeave(Long id, UserPrincipal guard) {
        StudentLeave l = requireGuardApproved(id);
        if (l.getLeaveTime() != null) {
            throw new BizException(400, "该学生已登记离校 " + fmt(l.getLeaveTime()));
        }
        l.setLeaveTime(LocalDateTime.now());
        l.setLeaveGuardId(guard.userId());
        leaveMapper.updateById(l);
    }

    /** 门卫登记返校（须先有离校记录） */
    public void registerReturn(Long id, UserPrincipal guard) {
        StudentLeave l = requireGuardApproved(id);
        if (l.getLeaveTime() == null) {
            throw new BizException(400, "尚未登记离校，请先登记离校");
        }
        if (l.getReturnTime() != null) {
            throw new BizException(400, "该学生已登记返校 " + fmt(l.getReturnTime()));
        }
        l.setReturnTime(LocalDateTime.now());
        l.setReturnGuardId(guard.userId());
        leaveMapper.updateById(l);
    }

    // ────────────────────────── 通用 ──────────────────────────

    /** 详情：提交家长本人 / 任何教师侧（含领导/管理员）/ 门卫 */
    public Map<String, Object> detail(Long id, UserPrincipal user) {
        StudentLeave l = require(id);
        checkReadable(l, user);
        Student s = studentsOf(List.of(l)).get(l.getStudentId());
        Map<String, Object> m = rowOf(l, namesOf(List.of(l)), s, classNames(s == null ? List.<Student>of() : List.of(s)));
        m.put("reason", l.getReason());
        m.put("approveNote", l.getApproveNote() == null ? "" : l.getApproveNote());
        return m;
    }

    /** 逐张凭证照片回图（病假条等；可见性同详情） */
    public String photoObject(Long id, int idx, UserPrincipal user) {
        StudentLeave l = require(id);
        checkReadable(l, user);
        List<String> objects = parsePhotos(l.getPhotos());
        if (idx < 0 || idx >= objects.size()) {
            throw new BizException(404, "照片不存在");
        }
        return objects.get(idx);
    }

    private void checkReadable(StudentLeave l, UserPrincipal user) {
        String role = user.role();
        if (l.getParentId().equals(user.userId())) {
            return;
        }
        if ("TEACHER".equals(role) || "HEAD_TEACHER".equals(role)
                || "LEADER".equals(role) || "ADMIN".equals(role) || "GUARD".equals(role)) {
            return;
        }
        throw new BizException(403, "无权查看该请假单");
    }

    private StudentLeave require(Long id) {
        StudentLeave l = leaveMapper.selectById(id);
        if (l == null) {
            throw new BizException(404, "请假单不存在");
        }
        return l;
    }

    private StudentLeave requirePending(Long id) {
        StudentLeave l = require(id);
        if (!StudentLeave.PENDING.equals(l.getStatus())) {
            throw new BizException(400, "该请假单已处理结束");
        }
        return l;
    }

    private StudentLeave requireGuardApproved(Long id) {
        StudentLeave l = require(id);
        if (!StudentLeave.APPROVED.equals(l.getStatus())) {
            throw new BizException(400, "仅已批准的请假单可登记离校/返校");
        }
        return l;
    }

    private void requireGuard(UserPrincipal user) {
        if (!"GUARD".equals(user.role())) {
            throw new BizException(403, "仅门卫账号可访问");
        }
    }

    /** 审批权（批31 收紧）：本班班主任；领导/管理员可代批兜底，任课教师只读 */
    private void requireApprover(UserPrincipal user, StudentLeave l) {
        String role = user.role();
        if ("LEADER".equals(role) || "ADMIN".equals(role)) {
            return;
        }
        if ("HEAD_TEACHER".equals(role)) {
            Student s = studentMapper.selectById(l.getStudentId());
            if (s != null && s.getClassId() != null) {
                Clazz c = clazzMapper.selectById(s.getClassId());
                if (c != null && user.userId().equals(c.getHeadTeacherId())) {
                    return;
                }
            }
        }
        throw new BizException(403, "学生请假由该班班主任审批（领导/管理员可代批）");
    }

    /** 班级→班主任 id 映射（列表行级 canApprove 标记用；无班主任的班不入表） */
    private Map<Long, Long> classHeadOf(List<Long> classIds) {
        List<Long> ids = classIds.stream().filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of()
                : clazzMapper.selectBatchIds(ids).stream()
                        .filter(c -> c.getHeadTeacherId() != null)
                        .collect(Collectors.toMap(Clazz::getId, Clazz::getHeadTeacherId, (a, b) -> a));
    }

    private void requireParentBound(UserPrincipal parent, Long studentId) {
        if (!"PARENT".equals(parent.role())) {
            throw new BizException(403, "仅家长账号可提交学生请假");
        }
        if (bindingMapper.selectCount(new LambdaQueryWrapper<ParentBinding>()
                .eq(ParentBinding::getParentUserId, parent.userId())
                .eq(ParentBinding::getStudentId, studentId)) == 0) {
            throw new BizException(403, "该学生未绑定当前家长账号");
        }
    }

    /** 按可见班级过滤（行级，单量小；join 查询不值得为 94 班引入） */
    private List<StudentLeave> filterByClass(List<StudentLeave> rows, List<Long> classIds) {
        Map<Long, Student> students = studentsOf(rows);
        return rows.stream().filter(l -> {
            Student s = students.get(l.getStudentId());
            return s != null && s.getClassId() != null && classIds.contains(s.getClassId());
        }).toList();
    }

    private boolean matchesKeyword(Map<String, Object> m, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String kw = q.trim();
        return String.valueOf(m.get("studentName")).contains(kw)
                || String.valueOf(m.get("studentNo")).contains(kw)
                || String.valueOf(m.get("className")).contains(kw);
    }

    private List<Map<String, Object>> toRows(List<StudentLeave> rows) {
        Map<Long, String> names = namesOf(rows);
        Map<Long, Student> students = studentsOf(rows);
        Map<Long, String> classNames = classNames(new ArrayList<>(students.values()));
        return rows.stream().map(l -> rowOf(l, names, students.get(l.getStudentId()), classNames)).collect(Collectors.toList());
    }

    private Map<String, Object> rowOf(StudentLeave l, Map<Long, String> names, Student s, Map<Long, String> classNames) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("studentId", l.getStudentId());
        m.put("studentName", s == null ? "(学生已删除)" : s.getName());
        m.put("studentNo", s == null ? null : s.getStudentNo());
        m.put("className", s == null || s.getClassId() == null ? null : classNames.get(s.getClassId()));
        m.put("leaveType", l.getLeaveType());
        m.put("startDate", l.getStartDate() == null ? null : l.getStartDate().toString());
        m.put("endDate", l.getEndDate() == null ? null : l.getEndDate().toString());
        m.put("status", l.getStatus());
        m.put("approverName", l.getApproverId() == null ? "" : names.getOrDefault(l.getApproverId(), ""));
        m.put("approveNote", l.getApproveNote() == null ? "" : l.getApproveNote());
        m.put("approveTime", l.getApproveTime());
        m.put("leaveTime", l.getLeaveTime());
        m.put("returnTime", l.getReturnTime());
        m.put("createTime", l.getCreateTime());
        m.put("reason", l.getReason());
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < parsePhotos(l.getPhotos()).size(); i++) {
            urls.add("/api/student-leave/file/" + l.getId() + "?idx=" + i); // 前端 fetchBlob 直取（同报修/微光）
        }
        m.put("photoCount", urls.size());
        m.put("photoUrls", urls);
        return m;
    }

    /** 家长姓名不外显（行内只出审批人/门卫名），names 收集 approver/guard id */
    private Map<Long, String> namesOf(List<StudentLeave> rows) {
        List<Long> ids = rows.stream()
                .flatMap(l -> java.util.stream.Stream.of(l.getApproverId(), l.getLeaveGuardId(), l.getReturnGuardId()))
                .filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));
    }

    private Map<Long, Student> studentsOf(List<StudentLeave> rows) {
        List<Long> ids = rows.stream().map(StudentLeave::getStudentId).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of()
                : studentMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(Student::getId, s -> s, (a, b) -> a));
    }

    private Map<Long, String> classNames(List<Student> students) {
        List<Long> ids = students.stream().map(Student::getClassId).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of()
                : clazzMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(Clazz::getId, Clazz::getName, (a, b) -> a));
    }

    private List<String> uploadPhotos(List<MultipartFile> photos) {
        List<String> objects = new ArrayList<>();
        if (photos == null) {
            return objects;
        }
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
            String objectName = "student_leave/" + UUID.randomUUID() + "." + ext;
            pdfStore.upload(objectName, new ByteArrayInputStream(bytes), bytes.length, p.getContentType());
            objects.add(objectName);
        }
        return objects;
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

    private static String fmt(LocalDateTime t) {
        return t == null ? "" : t.toString().replace('T', ' ').substring(0, 16);
    }
}
