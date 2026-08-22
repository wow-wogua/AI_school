package com.aischool.server.service.auth;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.Teach;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.TeachMapper;
import com.aischool.server.security.UserPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据权限（角色隔离）：
 * - ADMIN：全校
 * - HEAD_TEACHER（班主任）：所带班级
 * - TEACHER（任课教师）：任课班级（t_teach）
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final ClazzMapper clazzMapper;
    private final StudentMapper studentMapper;
    private final TeachMapper teachMapper;

    /** 当前用户可见的班级 id 列表；null 表示不受限（管理员） */
    public List<Long> visibleClassIds(UserPrincipal user) {
        return switch (user.role()) {
            case "ADMIN" -> null;
            case "HEAD_TEACHER" -> clazzMapper.selectList(new LambdaQueryWrapper<Clazz>()
                    .eq(Clazz::getHeadTeacherId, user.userId())).stream().map(Clazz::getId).toList();
            case "TEACHER" -> teachMapper.selectList(new LambdaQueryWrapper<Teach>()
                            .eq(Teach::getTeacherId, user.userId())).stream().map(Teach::getClassId).distinct().toList();
            default -> throw new BizException(403, "未知角色: " + user.role());
        };
    }

    /** 校验当前用户可访问指定学生，返回学生实体 */
    public Student checkStudentAccess(UserPrincipal user, Long studentId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BizException(404, "学生不存在");
        }
        List<Long> visible = visibleClassIds(user);
        if (visible != null && !visible.contains(student.getClassId())) {
            throw new BizException(403, "无权访问该学生（数据权限隔离）");
        }
        return student;
    }

    /** 校验当前用户可对指定班级发起操作（生成报告：管理员或该班班主任） */
    public void checkClassOperable(UserPrincipal user, Long classId) {
        if ("ADMIN".equals(user.role())) {
            return;
        }
        if ("HEAD_TEACHER".equals(user.role())) {
            Clazz clazz = clazzMapper.selectById(classId);
            if (clazz != null && user.userId().equals(clazz.getHeadTeacherId())) {
                return;
            }
        }
        throw new BizException(403, "只有管理员或该班班主任可执行此操作");
    }
}
