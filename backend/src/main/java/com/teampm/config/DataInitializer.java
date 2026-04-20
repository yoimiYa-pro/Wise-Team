package com.teampm.config;

import com.teampm.domain.Team;
import com.teampm.domain.TeamMember;
import com.teampm.domain.User;
import com.teampm.mapper.TeamMapper;
import com.teampm.mapper.TeamMemberMapper;
import com.teampm.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 应用启动时若库中无演示账号则写入 admin / manager / member 及示例团队，便于本地联调（生产应关闭或改为迁移数据）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userMapper.findByUsername("admin") == null) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("Admin@123"));
            admin.setDisplayName("系统管理员");
            admin.setRole("ADMIN");
            admin.setSkillsJson("{\"system\":1}");
            admin.setBaseCapacity(BigDecimal.valueOf(40));
            admin.setAvgPerformance(BigDecimal.valueOf(80));
            admin.setDelayHistoryScore(BigDecimal.ZERO);
            admin.setStatus(1);
            userMapper.insert(admin);
            log.info("Seeded admin / Admin@123");
        }
        if (userMapper.findByUsername("manager1") == null) {
            User m = new User();
            m.setUsername("manager1");
            m.setPasswordHash(passwordEncoder.encode("Admin@123"));
            m.setDisplayName("团队管理者");
            m.setRole("MANAGER");
            m.setSkillsJson("{\"coding\":0.85,\"manage\":0.9}");
            m.setBaseCapacity(BigDecimal.valueOf(40));
            m.setAvgPerformance(BigDecimal.valueOf(78));
            m.setDelayHistoryScore(BigDecimal.valueOf(0.15));
            m.setStatus(1);
            userMapper.insert(m);
        }
        if (userMapper.findByUsername("member1") == null) {
            User u = new User();
            u.setUsername("member1");
            u.setPasswordHash(passwordEncoder.encode("Admin@123"));
            u.setDisplayName("成员甲");
            u.setRole("MEMBER");
            u.setSkillsJson("{\"coding\":0.9,\"test\":0.6}");
            u.setBaseCapacity(BigDecimal.valueOf(40));
            u.setAvgPerformance(BigDecimal.valueOf(76));
            u.setDelayHistoryScore(BigDecimal.valueOf(0.1));
            u.setStatus(1);
            userMapper.insert(u);
        }
        if (userMapper.findByUsername("member2") == null) {
            User u = new User();
            u.setUsername("member2");
            u.setPasswordHash(passwordEncoder.encode("Admin@123"));
            u.setDisplayName("成员乙");
            u.setRole("MEMBER");
            u.setSkillsJson("{\"coding\":0.65,\"design\":0.8}");
            u.setBaseCapacity(BigDecimal.valueOf(40));
            u.setAvgPerformance(BigDecimal.valueOf(72));
            u.setDelayHistoryScore(BigDecimal.valueOf(0.35));
            u.setStatus(1);
            userMapper.insert(u);
        }
        User mgr = userMapper.findByUsername("manager1");
        User m1 = userMapper.findByUsername("member1");
        User m2 = userMapper.findByUsername("member2");
        if (mgr != null && teamMapper.findByManagerId(mgr.getId()).isEmpty()) {
            Team t = new Team();
            t.setName("示例研发团队");
            t.setGoal("交付任务管理与绩效演示项目");
            t.setAnnouncement("欢迎使用本系统");
            t.setStatus("ACTIVE");
            t.setManagerId(mgr.getId());
            teamMapper.insert(t);
            Long tid = t.getId();
            addMember(tid, m1.getId(), "APPROVED");
            addMember(tid, m2.getId(), "APPROVED");
            addMember(tid, mgr.getId(), "APPROVED");
            log.info("Seeded demo team id={}", tid);
        }
    }

    private void addMember(Long teamId, Long userId, String status) {
        if (userId == null) {
            return;
        }
        if (teamMemberMapper.findByTeamAndUser(teamId, userId) != null) {
            return;
        }
        TeamMember tm = new TeamMember();
        tm.setTeamId(teamId);
        tm.setUserId(userId);
        tm.setApprovalStatus(status);
        teamMemberMapper.insert(tm);
    }
}
