package com.teampm.service;

import com.teampm.domain.User;
import com.teampm.exception.ApiException;
import com.teampm.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public List<User> listAll() {
        return userMapper.findAll();
    }

    public User get(Long id) {
        User u = userMapper.findById(id);
        if (u == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return u;
    }

    @Transactional
    public User create(User input, String rawPassword, Long actorId) {
        if (userMapper.findByUsername(input.getUsername()) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "用户名已存在");
        }
        if (rawPassword == null || rawPassword.length() < 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码至少 4 位");
        }
        input.setPasswordHash(passwordEncoder.encode(rawPassword));
        if (input.getBaseCapacity() == null) {
            input.setBaseCapacity(BigDecimal.valueOf(40));
        }
        if (input.getAvgPerformance() == null) {
            input.setAvgPerformance(BigDecimal.valueOf(75));
        }
        if (input.getDelayHistoryScore() == null) {
            input.setDelayHistoryScore(BigDecimal.ZERO);
        }
        if (input.getStatus() == null) {
            input.setStatus(1);
        }
        userMapper.insert(input);
        auditService.log(actorId, "USER_CREATE", "User", input.getId(), input.getUsername());
        return userMapper.findById(input.getId());
    }

    @Transactional
    public User update(User input, Long actorId) {
        User existing = get(input.getId());
        if (input.getStatus() != null && input.getStatus() == 0 && actorId.equals(input.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不能禁用自己的账号");
        }
        existing.setDisplayName(input.getDisplayName());
        existing.setRole(input.getRole());
        existing.setSkillsJson(input.getSkillsJson());
        existing.setBaseCapacity(input.getBaseCapacity());
        existing.setAvgPerformance(input.getAvgPerformance());
        existing.setDelayHistoryScore(input.getDelayHistoryScore());
        existing.setStatus(input.getStatus());
        userMapper.update(existing);
        auditService.log(actorId, "USER_UPDATE", "User", existing.getId(), null);
        return userMapper.findById(existing.getId());
    }

    @Transactional
    public void resetPassword(Long id, String rawPassword, Long actorId) {
        get(id);
        if (rawPassword == null || rawPassword.length() < 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码至少 4 位");
        }
        userMapper.updatePassword(id, passwordEncoder.encode(rawPassword));
        auditService.log(actorId, "USER_PASSWORD_RESET", "User", id, null);
    }

    @Transactional
    public User setStatus(Long id, int status, Long actorId) {
        if (actorId.equals(id) && status == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不能禁用自己的账号");
        }
        User u = get(id);
        u.setStatus(status);
        userMapper.update(u);
        auditService.log(actorId, status == 0 ? "USER_DISABLE" : "USER_ENABLE", "User", id, u.getUsername());
        return userMapper.findById(id);
    }
}
