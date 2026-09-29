package com.aischool.server.service.notify;

import com.aischool.server.entity.Notification;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.NotificationMapper;
import com.aischool.server.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知中心（批29）：App 内点对点通知落 t_notification，待办类同步推企微群。
 * 所有发送入口吞异常——通知失败绝不阻断业务主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final WecomService wecomService;

    /** 落一条通知（唯一写入口；异常吞掉只记日志） */
    public void send(Long userId, String type, String title, String content, String link) {
        if (userId == null) {
            return;
        }
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(type);
            n.setTitle(title == null ? "" : title);
            n.setContent(content);
            n.setLink(link);
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("通知写入失败 user={} title={}：{}", userId, title, e.getMessage());
        }
    }

    /** 通知所有启用中的指定角色（账号审批/告警/周报广播用） */
    public void sendToRoles(List<String> roles, String type, String title, String content, String link) {
        userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getRole, roles).eq(User::getStatus, 1))
                .forEach(u -> send(u.getId(), type, title, content, link));
    }

    // ────────────────────────── 事件格式化（批29 接线点专用） ──────────────────────────

    /** OA 提交/流转到下一级：通知当前级审批人（App 内 + 企微群） */
    public void oaTodo(Long approverId, String typeName, String title, Long formId, String applicantName) {
        send(approverId, Notification.OA_TODO, "待审批：" + typeName,
                applicantName + " 提交的「" + title + "」等待您审批", "/oa");
        wecomService.pushApprovals("【新审批】" + applicantName + " 提交了" + typeName
                + "：" + title + "（单号 #" + formId + "）");
    }

    /** OA 终态：通知申请人审批结果（不推群，防刷屏） */
    public void oaResult(Long applicantId, String typeName, String title, boolean approved, String note) {
        String verdict = approved ? "已审批通过" : "已被驳回";
        send(applicantId, Notification.OA_RESULT, typeName + verdict,
                "您提交的「" + title + "」" + verdict
                        + (note == null || note.isBlank() ? "" : "，意见：" + note), "/oa");
    }

    /** 学生请假提交：通知本班班主任（批31 收紧后任课教师不再推送） */
    public void leaveTodo(List<Long> teacherIds, String studentName, String className,
                          String leaveType, String start, String end) {
        String range = start.equals(end) ? start : start + "~" + end;
        for (Long tid : teacherIds) {
            send(tid, Notification.LEAVE_TODO, "待审批：学生请假",
                    className + " " + studentName + " 家长提交了" + leaveType + "（" + range + "）", "/leave");
        }
        wecomService.pushApprovals("【学生请假】" + className + " " + studentName
                + " 家长提交了" + leaveType + "（" + range + "），等待班主任审批");
    }

    /** 学生请假审批结果：通知提交家长 */
    public void leaveResult(Long parentId, String studentName, boolean approved, String approverName, String note) {
        String verdict = approved ? "已批准" : "已被驳回";
        String extra = note == null || note.isBlank() ? "" : "，老师备注：" + note;
        send(parentId, Notification.LEAVE_RESULT, "请假" + verdict,
                studentName + " 的请假申请" + verdict + "（审批人：" + approverName + "）" + extra, "/p/leave");
    }

    /** 账号/注册待审批：通知全部启用中的管理员+领导（App 链接按角色分流）；self=教师自助注册 */
    public void registerTodo(String targetName, String targetRoleName, String requesterName, boolean self) {
        String detail = self
                ? targetName + " 的注册申请等待您审批"
                : targetName + "（由 " + requesterName + " 发起）等待您审批";
        wecomService.pushApprovals(self
                ? "【教师注册】" + targetName + " 提交了注册申请，等待审批"
                : "【待审批】" + requesterName + " 申请：" + targetName + "（" + targetRoleName + "）");
        userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getRole, "ADMIN", "LEADER").eq(User::getStatus, 1))
                .forEach(u -> send(u.getId(), Notification.REGISTER_TODO,
                        "待审批：" + targetRoleName + "账号", detail,
                        "ADMIN".equals(u.getRole()) ? "/admin?tab=roleRequest" : "/l/approvals"));
    }
}
