package com.teampm.web;

import com.teampm.domain.Team;
import com.teampm.dto.AssignableUserDto;
import com.teampm.dto.TeamMemberView;
import com.teampm.dto.TeamOverviewDto;
import com.teampm.security.SecurityUtils;
import com.teampm.security.UserPrincipal;
import com.teampm.service.TeamService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping("/managed")
    public List<Team> managed(@AuthenticationPrincipal UserPrincipal p) {
        SecurityUtils.requireUser();
        return teamService.listForManager(p.getId());
    }

    @GetMapping("/member-of")
    public List<Team> memberOf(@AuthenticationPrincipal UserPrincipal p) {
        SecurityUtils.requireUser();
        return teamService.listForMember(p.getId());
    }

    @GetMapping("/member-of/overview")
    public List<TeamOverviewDto> memberOverview() {
        return teamService.listMemberOverview(SecurityUtils.requireUser());
    }

    @GetMapping("/overview")
    public List<TeamOverviewDto> overview() {
        return teamService.listOverview(SecurityUtils.requireUser());
    }

    @PostMapping
    public Team create(@RequestBody Team body, @AuthenticationPrincipal UserPrincipal p) {
        return teamService.create(body, SecurityUtils.requireUser());
    }

    @PutMapping("/{id}")
    public Team update(@PathVariable Long id, @RequestBody Team body, @AuthenticationPrincipal UserPrincipal p) {
        return teamService.update(id, body, SecurityUtils.requireUser());
    }

    @PostMapping("/{id}/archive")
    public void archive(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
        teamService.archive(id, SecurityUtils.requireUser());
    }

    @PostMapping("/{id}/join")
    public void join(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
        teamService.requestJoin(id, SecurityUtils.requireUser());
    }

    @GetMapping("/{id}/members")
    public List<TeamMemberView> members(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
        return teamService.members(id, SecurityUtils.requireUser());
    }

    @GetMapping("/{id}/invite-candidates")
    public List<AssignableUserDto> inviteCandidates(@PathVariable Long id) {
        return teamService.listInviteCandidates(id, SecurityUtils.requireUser());
    }

    @PostMapping("/{id}/members")
    public void addMember(@PathVariable Long id, @RequestBody AddMemberReq req) {
        teamService.addMember(id, req.getUserId(), SecurityUtils.requireUser());
    }

    @DeleteMapping("/{id}/members/{memberId}")
    public void removeMember(@PathVariable Long id, @PathVariable Long memberId) {
        teamService.removeMember(id, memberId, SecurityUtils.requireUser());
    }

    @GetMapping("/{id}/assignable-users")
    public List<AssignableUserDto> assignableUsers(@PathVariable Long id) {
        return teamService.listAssignableUsers(id, SecurityUtils.requireUser());
    }

    @PostMapping("/{id}/members/{memberId}/approve")
    public void approve(@PathVariable Long id, @PathVariable Long memberId, @AuthenticationPrincipal UserPrincipal p) {
        teamService.approveMember(memberId, SecurityUtils.requireUser());
    }

    @PostMapping("/{id}/members/{memberId}/reject")
    public void reject(@PathVariable Long id, @PathVariable Long memberId, @AuthenticationPrincipal UserPrincipal p) {
        teamService.rejectMember(memberId, SecurityUtils.requireUser());
    }

    @GetMapping("/{id}")
    public Team one(@PathVariable Long id) {
        return teamService.requireTeam(id);
    }

    @Data
    public static class AddMemberReq {
        private Long userId;
    }
}
