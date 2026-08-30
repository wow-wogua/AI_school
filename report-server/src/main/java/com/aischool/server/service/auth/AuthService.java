package com.aischool.server.service.auth;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.JwtService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** 登录失败防护：同用户名 15 分钟内 5 次失败 → 锁 10 分钟（单实例内存实现，重启即解） */
    private static final int MAX_FAILS = 5;
    private static final long WINDOW_MS = 15 * 60 * 1000;
    private static final long LOCK_MS = 10 * 60 * 1000;

    /** 滑动窗口内失败计数（可变） */
    private static final class FailCount {
        int count;
        long windowStart;
    }

    private final Map<String, FailCount> loginFails = new ConcurrentHashMap<>();
    private final Map<String, Long> lockUntil = new ConcurrentHashMap<>();

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public Map<String, Object> login(String username, String password) {
        Long locked = lockUntil.get(username);
        if (locked != null && locked > System.currentTimeMillis()) {
            long min = (locked - System.currentTimeMillis()) / 60000 + 1;
            throw new BizException(429, "失败次数过多，请 " + min + " 分钟后再试");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username).last("LIMIT 1"));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            recordFail(username);
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(403, "账号已停用");
        }
        loginFails.remove(username);
        lockUntil.remove(username);
        String token = jwtService.issue(user.getId(), user.getUsername(), user.getRealName(), user.getRole());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", token);
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("id", user.getId());
        u.put("username", user.getUsername());
        u.put("realName", user.getRealName());
        u.put("role", user.getRole());
        m.put("user", u);
        return m;
    }

    /** 修改自己的密码：验证旧密码后更新（新密码至少 6 位） */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "账号不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BizException(400, "旧密码不正确");
        }
        if (newPassword.length() < 6) {
            throw new BizException(400, "新密码至少 6 位");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    private void recordFail(String username) {
        long now = System.currentTimeMillis();
        FailCount f = loginFails.compute(username, (k, v) -> {
            if (v == null || now - v.windowStart > WINDOW_MS) {
                FailCount n = new FailCount();
                n.windowStart = now;
                return n;
            }
            return v;
        });
        f.count++;
        if (f.count >= MAX_FAILS) {
            lockUntil.put(username, now + LOCK_MS);
            loginFails.remove(username);
        }
    }
}
