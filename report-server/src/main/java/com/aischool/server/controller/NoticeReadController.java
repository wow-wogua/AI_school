package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.ContentItem;
import com.aischool.server.entity.NoticeRead;
import com.aischool.server.entity.ParentBinding;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.ContentItemMapper;
import com.aischool.server.mapper.NoticeReadMapper;
import com.aischool.server.mapper.ParentBindingMapper;
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.AuthUtil;
import com.aischool.server.service.auth.PermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通知已读回执（批9）：家长打开通知详情即打点（uk 幂等）；
 * 管理端统计已读率+未读家长名单。应读口径=该通知可见范围内已绑定的家长。
 */
@RestController
@RequiredArgsConstructor
public class NoticeReadController {

    private final ContentItemMapper contentMapper;
    private final NoticeReadMapper readMapper;
    private final ParentBindingMapper bindingMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final PermissionService permissionService;

    /** 打点：家长打开通知详情（仅已发布且范围可见；重复打点幂等） */
    @PostMapping("/api/parent/notice/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id) {
        if (!"PARENT".equals(AuthUtil.current().role())) {
            throw new BizException(403, "仅家长账号打点已读");
        }
        ContentItem it = requireNotice(id);
        if ("CLASS".equals(it.getScope()) && !boundClassIds().contains(it.getClassId())) {
            throw new BizException(403, "该通知未对您开放");
        }
        NoticeRead r = new NoticeRead();
        r.setNoticeId(id);
        r.setUserId(AuthUtil.current().userId());
        try {
            readMapper.insert(r);
        } catch (DuplicateKeyException e) {
            // 已读过：幂等
        }
        return ApiResponse.ok();
    }

    /** 回执统计：应读=可见范围已绑定家长；read=已读；unread=未读名单（姓名/手机号） */
    @GetMapping("/api/admin/notice/{id}/read-stats")
    public ApiResponse<Map<String, Object>> stats(@PathVariable Long id) {
        permissionService.checkAdminAccess("只有管理员可查回执");
        ContentItem it = requireNotice(id);
        // 应读家长：ALL=全部已绑定家长；CLASS=绑定该班学生的家长
        List<Long> studentIds = "CLASS".equals(it.getScope())
                ? studentMapper.selectList(new LambdaQueryWrapper<Student>()
                        .eq(Student::getClassId, it.getClassId())
                        .and(q -> q.isNull(Student::getStatus).or().notIn(Student::getStatus, "毕业", "转出")))
                        .stream().map(Student::getId).toList()
                : studentMapper.selectList(null).stream()
                        .filter(s -> s.getStatus() == null || !List.of("毕业", "转出").contains(s.getStatus()))
                        .map(Student::getId).toList();
        Set<Long> should = studentIds.isEmpty() ? Set.of()
                : bindingMapper.selectList(new LambdaQueryWrapper<ParentBinding>()
                        .in(ParentBinding::getStudentId, studentIds))
                        .stream().map(ParentBinding::getParentUserId).collect(Collectors.toSet());
        Map<Long, User> users = should.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(should).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Set<Long> readIds = readMapper.selectList(new LambdaQueryWrapper<NoticeRead>()
                .eq(NoticeRead::getNoticeId, id))
                .stream().map(NoticeRead::getUserId).collect(Collectors.toSet());
        List<Map<String, Object>> unread = new ArrayList<>();
        for (Long uid : should) {
            if (readIds.contains(uid)) {
                continue;
            }
            User u = users.get(uid);
            if (u == null || (u.getStatus() != null && u.getStatus() != 1)) {
                continue; // 停用账号不计
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", u.getRealName());
            m.put("phone", u.getUsername());
            unread.add(m);
        }
        long readCount = should.stream().filter(readIds::contains).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", it.getTitle());
        out.put("total", should.size());
        out.put("read", readCount);
        out.put("unread", unread);
        return ApiResponse.ok(out);
    }

    private ContentItem requireNotice(Long id) {
        ContentItem it = contentMapper.selectById(id);
        if (it == null || !ContentItem.TYPE_NOTICE.equals(it.getType())
                || it.getStatus() == null || it.getStatus() != 1) {
            throw new BizException(404, "通知不存在或未发布");
        }
        return it;
    }

    private List<Long> boundClassIds() {
        List<ParentBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<ParentBinding>()
                .eq(ParentBinding::getParentUserId, AuthUtil.current().userId()));
        List<Long> sids = bindings.stream().map(ParentBinding::getStudentId).toList();
        return sids.isEmpty() ? List.of()
                : studentMapper.selectBatchIds(sids).stream()
                        .map(Student::getClassId).filter(c -> c != null).distinct().toList();
    }
}
