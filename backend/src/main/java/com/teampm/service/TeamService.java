package com.teampm.service;

import com.teampm.domain.Team;
import com.teampm.domain.TeamMember;
import com.teampm.domain.User;
import com.teampm.dto.AssignableUserDto;
import com.teampm.dto.TeamMemberView;
import com.teampm.dto.TeamOverviewDto;
import com.teampm.exception.ApiException;
import com.teampm.mapper.TeamMapper;
import com.teampm.mapper.TeamMemberMapper;
import com.teampm.mapper.UserMapper;
import com.teampm.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 团队与成员关系：{@link #assertManager} / {@link #assertMemberApproved} 等为各业务服务统一的权限断言入口。
 */
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final AuditService auditService;

    public Team requireTeam(Long teamId) {
        Team t = teamMapper.findById(teamId);
        if (t == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "团队不存在");
        }
        return t;
    }

    public void assertManager(UserPrincipal p, Team team) {
        if ("ADMIN".equals(p.getRole())) {
            return;
        }
        if (!"MANAGER".equals(p.getRole()) || !team.getManagerId().equals(p.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "仅团队管理者可操作");
        }
    }

    public void assertMemberApproved(UserPrincipal p, Long teamId) {
        if ("ADMIN".equals(p.getRole())) {
            return;
        }
        TeamMember tm = teamMemberMapper.findByTeamAndUser(teamId, p.getId());
        if (tm == null || !"APPROVED".equals(tm.getApprovalStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "非本团队成员");
        }
    }

    public boolean isApprovedMember(Long teamId, Long userId) {
        TeamMember tm = teamMemberMapper.findByTeamAndUser(teamId, userId);
        return tm != null && "APPROVED".equals(tm.getApprovalStatus());
    }

    public List<Team> listForManager(Long managerId) {
        return teamMapper.findByManagerId(managerId);
    }

    public List<Team> listAll() {
        return teamMapper.findAll();
    }

    public List<Team> listForMember(Long userId) {
        return teamMapper.findTeamsForApprovedMember(userId);
    }

    public List<TeamOverviewDto> listOverview(UserPrincipal actor) {
        List<Team> teams;
        if ("ADMIN".equals(actor.getRole())) {
            teams = teamMapper.findAll();
        } else if ("MANAGER".equals(actor.getRole())) {
            teams = teamMapper.findByManagerId(actor.getId());
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "无权查看团队列表");
        }
        return toOverviewDtos(teams);
    }

    /** 当前用户作为已批准成员所在团队的概览（成员只读列表用） */
    public List<TeamOverviewDto> listMemberOverview(UserPrincipal actor) {
        List<Team> teams = teamMapper.findTeamsForApprovedMember(actor.getId());
        return toOverviewDtos(teams);
    }

    private List<TeamOverviewDto> toOverviewDtos(List<Team> teams) {
        List<TeamOverviewDto> out = new ArrayList<>(teams.size());
        for (Team t : teams) {
            User mgr = userMapper.findById(t.getManagerId());
            int n = teamMemberMapper.countApprovedByTeamId(t.getId());
            out.add(new TeamOverviewDto(
                    t.getId(),
                    t.getName(),
                    t.getGoal(),
                    t.getAnnouncement(),
                    t.getStatus(),
                    t.getManagerId(),
                    mgr != null ? mgr.getUsername() : "-",
                    n));
        }
        return out;
    }

    @Transactional
    public Team create(Team team, UserPrincipal actor) {
        if (!"MANAGER".equals(actor.getRole()) && !"ADMIN".equals(actor.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "无权创建团队");
        }
        if ("MANAGER".equals(actor.getRole())) {
            team.setManagerId(actor.getId());
        }
        if (team.getManagerId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "需指定管理者");
        }
        team.setStatus("ACTIVE");
        teamMapper.insert(team);
        Long tid = team.getId();
        if (teamMemberMapper.findByTeamAndUser(tid, team.getManagerId()) == null) {
            TeamMember tm = new TeamMember();
            tm.setTeamId(tid);
            tm.setUserId(team.getManagerId());
            tm.setApprovalStatus("APPROVED");
            teamMemberMapper.insert(tm);
        }
        auditService.log(actor.getId(), "TEAM_CREATE", "Team", tid, team.getName());
        return teamMapper.findById(tid);
    }

    @Transactional
    public Team update(Long teamId, Team patch, UserPrincipal actor) {
        Team t = requireTeam(teamId);
        assertManager(actor, t);
        t.setName(patch.getName());
        t.setGoal(patch.getGoal());
        t.setAnnouncement(patch.getAnnouncement());
        teamMapper.update(t);
        auditService.log(actor.getId(), "TEAM_UPDATE", "Team", teamId, null);
        return teamMapper.findById(teamId);
    }

    @Transactional
    public void archive(Long teamId, UserPrincipal actor) {
        Team t = requireTeam(teamId);
        assertManager(actor, t);
        t.setStatus("ARCHIVED");
        teamMapper.update(t);
        auditService.log(actor.getId(), "TEAM_ARCHIVE", "Team", teamId, null);
    }

    @Transactional
    public void deleteByAdmin(Long teamId, Long actorId) {
        requireTeam(teamId);
        teamMapper.deleteById(teamId);
        auditService.log(actorId, "TEAM_DELETE", "Team", teamId, null);
    }

    @Transactional
    public void requestJoin(Long teamId, UserPrincipal user) {
        requireTeam(teamId);
        TeamMember existing = teamMemberMapper.findByTeamAndUser(teamId, user.getId());
        if (existing != null) {
            throw new ApiException(HttpStatus.CONFLICT, "已申请或已在团队中");
        }
        TeamMember tm = new TeamMember();
        tm.setTeamId(teamId);
        tm.setUserId(user.getId());
        tm.setApprovalStatus("PENDING");
        teamMemberMapper.insert(tm);
    }

    public List<TeamMemberView> members(Long teamId, UserPrincipal actor) {
        Team t = requireTeam(teamId);
        assertManager(actor, t);
        List<TeamMember> list = teamMemberMapper.findByTeamId(teamId);
        List<TeamMemberView> out = new ArrayList<>(list.size());
        for (TeamMember tm : list) {
            User u = userMapper.findById(tm.getUserId());
            out.add(new TeamMemberView(
                    tm.getId(),
                    tm.getTeamId(),
                    tm.getUserId(),
                    tm.getApprovalStatus(),
                    u != null ? u.getUsername() : "?",
                    u != null ? u.getDisplayName() : null,
                    tm.getJoinedAt()));
        }
        return out;
    }

    /** 可邀请加入的用户：已启用且尚未以「已通过」身份在本团队中的用户（含待审/已拒绝/未申请） */
    public List<AssignableUserDto> listInviteCandidates(Long teamId, UserPrincipal actor) {
        Team t = requireTeam(teamId);
        assertManager(actor, t);
        Set<Long> approved = new HashSet<>(teamMemberMapper.findApprovedUserIds(teamId));
        List<AssignableUserDto> out = new ArrayList<>();
        for (User u : userMapper.findAll()) {
            if (u.getStatus() != null && u.getStatus() == 0) {
                continue;
            }
            if (approved.contains(u.getId())) {
                continue;
            }
            out.add(new AssignableUserDto(u.getId(), u.getUsername(), u.getDisplayName()));
        }
        return out;
    }

    @Transactional
    public void addMember(Long teamId, Long userId, UserPrincipal actor) {
        Team t = requireTeam(teamId);
        assertManager(actor, t);
        if (userId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请指定用户");
        }
        User u = userMapper.findById(userId);
        if (u == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户不存在");
        }
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该用户已禁用，无法加入团队");
        }
        TeamMember existing = teamMemberMapper.findByTeamAndUser(teamId, userId);
        if (existing != null) {
            if ("APPROVED".equals(existing.getApprovalStatus())) {
                throw new ApiException(HttpStatus.CONFLICT, "该用户已在团队中");
            }
            if ("PENDING".equals(existing.getApprovalStatus())) {
                teamMemberMapper.updateApproval(existing.getId(), "APPROVED");
                auditService.log(actor.getId(), "TEAM_MEMBER_APPROVE", "TeamMember", existing.getId(), "add-flow");
                return;
            }
            if ("REJECTED".equals(existing.getApprovalStatus())) {
                teamMemberMapper.updateApproval(existing.getId(), "APPROVED");
                auditService.log(actor.getId(), "TEAM_MEMBER_ADD", "TeamMember", existing.getId(), "reinvite");
                return;
            }
        }
        TeamMember tm = new TeamMember();
        tm.setTeamId(teamId);
        tm.setUserId(userId);
        tm.setApprovalStatus("APPROVED");
        teamMemberMapper.insert(tm);
        auditService.log(actor.getId(), "TEAM_MEMBER_ADD", "TeamMember", tm.getId(), String.valueOf(userId));
    }

    @Transactional
    public void removeMember(Long teamId, Long memberRowId, UserPrincipal actor) {
        Team t = requireTeam(teamId);
        assertManager(actor, t);
        TeamMember tm = teamMemberMapper.findById(memberRowId);
        if (tm == null || !tm.getTeamId().equals(teamId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "成员记录不存在");
        }
        if (tm.getUserId().equals(t.getManagerId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不可移除团队管理者");
        }
        teamMemberMapper.deleteById(memberRowId);
        auditService.log(actor.getId(), "TEAM_MEMBER_REMOVE", "TeamMember", memberRowId, null);
    }

    /** 已通过审核的成员，供任务指派等下拉使用（管理者/管理员） */
    public List<AssignableUserDto> listAssignableUsers(Long teamId, UserPrincipal actor) {
        Team t = requireTeam(teamId);
        assertManager(actor, t);
        List<Long> ids = teamMemberMapper.findApprovedUserIds(teamId);
        List<AssignableUserDto> out = new ArrayList<>(ids.size());
        for (Long uid : ids) {
            User u = userMapper.findById(uid);
            if (u != null && u.getStatus() != null && u.getStatus() != 0) {
                out.add(new AssignableUserDto(uid, u.getUsername(), u.getDisplayName()));
            }
        }
        return out;
    }

    @Transactional
    public void approveMember(Long memberRowId, UserPrincipal actor) {
        TeamMember tm = teamMemberMapper.findById(memberRowId);
        if (tm == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "申请不存在");
        }
        Team t = requireTeam(tm.getTeamId());
        assertManager(actor, t);
        teamMemberMapper.updateApproval(memberRowId, "APPROVED");
        auditService.log(actor.getId(), "TEAM_MEMBER_APPROVE", "TeamMember", memberRowId, null);
    }

    @Transactional
    public void rejectMember(Long memberRowId, UserPrincipal actor) {
        TeamMember tm = teamMemberMapper.findById(memberRowId);
        if (tm == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "申请不存在");
        }
        Team t = requireTeam(tm.getTeamId());
        assertManager(actor, t);
        teamMemberMapper.updateApproval(memberRowId, "REJECTED");
        auditService.log(actor.getId(), "TEAM_MEMBER_REJECT", "TeamMember", memberRowId, null);
    }
}
