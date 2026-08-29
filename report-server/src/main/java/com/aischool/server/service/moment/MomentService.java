package com.aischool.server.service.moment;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.Moment;
import com.aischool.server.entity.MomentStudent;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.MomentMapper;
import com.aischool.server.mapper.MomentStudentMapper;
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.UserPrincipal;
import com.aischool.server.service.auth.DataScopeService;
import com.aischool.server.service.report.PdfStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 微光信箱：教师随手拍 → MinIO → 关联多名学生。
 * 露出：班级页「本周微光」轮播 / 学生详情「TA的闪光时刻」/ 成长记录流（FeedService 混排）。
 */
@Service
@RequiredArgsConstructor
public class MomentService {

    private static final long MAX_SIZE = 10L * 1024 * 1024;

    private final MomentMapper momentMapper;
    private final MomentStudentMapper momentStudentMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final DataScopeService dataScope;
    private final PdfStoreService pdfStore;

    /** 创建一条微光：照片存 MinIO（moment/{classId}/{uuid}.{ext}）+ 主表 + 学生关联 */
    @Transactional
    public Map<String, Object> create(UserPrincipal user, Long classId, List<Long> studentIds,
                                      String sceneTag, String note, MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new BizException(400, "请先拍照或选择照片");
        }
        if (photo.getSize() > MAX_SIZE) {
            throw new BizException(400, "照片不能超过 10MB");
        }
        String original = photo.getOriginalFilename() == null ? "" : photo.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("png")) {
            throw new BizException(400, "仅支持 jpg/jpeg/png 格式");
        }
        if (sceneTag == null || sceneTag.isBlank()) {
            throw new BizException(400, "请选择场景标签");
        }
        if (note != null && note.length() > 500) {
            throw new BizException(400, "备注不能超过 500 字");
        }
        if (studentIds == null || studentIds.isEmpty()) {
            throw new BizException(400, "请选择至少一名学生");
        }
        // 班级可操作 + 学生都属于该班
        if (!"ADMIN".equals(user.role())) {
            dataScope.checkClassOperable(user, classId);
        }
        List<Student> students = studentMapper.selectBatchIds(studentIds);
        if (students.size() != studentIds.size()
                || students.stream().anyMatch(s -> !classId.equals(s.getClassId()))) {
            throw new BizException(400, "存在不属于该班级的学生");
        }

        byte[] bytes;
        try {
            bytes = photo.getBytes();
        } catch (Exception e) {
            throw new BizException(400, "读取照片失败");
        }
        String objectName = "moment/" + classId + "/" + UUID.randomUUID() + "." + ext;
        pdfStore.upload(objectName, new ByteArrayInputStream(bytes), bytes.length, photo.getContentType());

        Moment m = new Moment();
        m.setTeacherId(user.userId());
        m.setClassId(classId);
        m.setPhotoUrl(objectName);
        m.setSceneTag(sceneTag.trim());
        m.setNote(note == null ? null : note.trim());
        momentMapper.insert(m);
        for (Long sid : studentIds) {
            MomentStudent ms = new MomentStudent();
            ms.setMomentId(m.getId());
            ms.setStudentId(sid);
            momentStudentMapper.insert(ms);
        }
        return Map.of("momentId", m.getId());
    }

    /** 班级最近微光（班级页轮播；含关联学生姓名与记录教师） */
    public List<Map<String, Object>> listByClass(UserPrincipal user, Long classId, int limit) {
        List<Long> visible = dataScope.visibleClassIds(user);
        if (visible != null && !visible.contains(classId)) {
            throw new BizException(403, "无该班级数据权限");
        }
        List<Moment> moments = momentMapper.selectList(new LambdaQueryWrapper<Moment>()
                .eq(Moment::getClassId, classId)
                .orderByDesc(Moment::getCreateTime).orderByDesc(Moment::getId)
                .last("LIMIT " + Math.min(limit, 50)));
        return assemble(moments);
    }

    /** 某学生的微光（学生详情「TA的闪光时刻」） */
    public List<Map<String, Object>> listByStudent(UserPrincipal user, Long studentId, int limit) {
        dataScope.checkStudentAccess(user, studentId);
        List<Long> momentIds = momentStudentMapper.selectList(new LambdaQueryWrapper<MomentStudent>()
                        .eq(MomentStudent::getStudentId, studentId))
                .stream().map(MomentStudent::getMomentId).toList();
        if (momentIds.isEmpty()) {
            return List.of();
        }
        List<Moment> moments = momentMapper.selectList(new LambdaQueryWrapper<Moment>()
                .in(Moment::getId, momentIds)
                .orderByDesc(Moment::getCreateTime).orderByDesc(Moment::getId)
                .last("LIMIT " + Math.min(limit, 50)));
        return assemble(moments);
    }

    /** 删除：仅记录教师本人或管理员；连带删学生关联与 MinIO 对象 */
    @Transactional
    public void delete(UserPrincipal user, Long id) {
        Moment m = momentMapper.selectById(id);
        if (m == null) {
            throw new BizException(404, "微光记录不存在");
        }
        if (!m.getTeacherId().equals(user.userId()) && !"ADMIN".equals(user.role())) {
            throw new BizException(403, "仅记录教师本人或管理员可删除");
        }
        momentMapper.deleteById(id);
        momentStudentMapper.delete(new LambdaQueryWrapper<MomentStudent>()
                .eq(MomentStudent::getMomentId, id));
        pdfStore.delete(m.getPhotoUrl());
    }

    /** 批量组装视图：students[{id,name}] + teacherName + 可直接访问的 photoUrl */
    public List<Map<String, Object>> assemble(List<Moment> moments) {
        if (moments.isEmpty()) {
            return List.of();
        }
        List<Long> ids = moments.stream().map(Moment::getId).toList();
        Map<Long, List<MomentStudent>> byMoment = momentStudentMapper.selectList(
                        new LambdaQueryWrapper<MomentStudent>().in(MomentStudent::getMomentId, ids))
                .stream().collect(Collectors.groupingBy(MomentStudent::getMomentId));
        Map<Long, Student> stuById = studentMapper.selectBatchIds(
                        byMoment.values().stream().flatMap(List::stream)
                                .map(MomentStudent::getStudentId).distinct().toList())
                .stream().collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a));
        Map<Long, String> teacherNames = userMapper.selectBatchIds(
                        moments.stream().map(Moment::getTeacherId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));

        List<Map<String, Object>> out = new ArrayList<>();
        for (Moment m : moments) {
            List<Map<String, Object>> students = byMoment.getOrDefault(m.getId(), List.of()).stream()
                    .map(ms -> stuById.get(ms.getStudentId()))
                    .filter(s -> s != null)
                    .map(s -> Map.<String, Object>of("id", s.getId(), "name", s.getName()))
                    .toList();
            out.add(Map.of(
                    "id", m.getId(),
                    "note", m.getNote() == null ? "" : m.getNote(),
                    "sceneTag", m.getSceneTag(),
                    "createTime", m.getCreateTime(),
                    "teacherName", teacherNames.getOrDefault(m.getTeacherId(), ""),
                    "students", students,
                    "photoUrl", "/api/moment/file/" + m.getId()));
        }
        return out;
    }
}
