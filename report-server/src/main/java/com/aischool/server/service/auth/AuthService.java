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

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public Map<String, Object> login(String username, String password) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username).last("LIMIT 1"));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(403, "账号已停用");
        }
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
}
