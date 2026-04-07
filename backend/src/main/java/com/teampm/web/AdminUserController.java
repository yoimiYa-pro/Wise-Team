package com.teampm.web;

import com.teampm.domain.User;
import com.teampm.security.SecurityUtils;
import com.teampm.service.UserAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserAdminService userAdminService;

    @GetMapping
    public List<User> list() {
        List<User> list = userAdminService.listAll();
        list.forEach(u -> u.setPasswordHash(null));
        return list;
    }

    @GetMapping("/{id}")
    public User get(@PathVariable Long id) {
        User u = userAdminService.get(id);
        u.setPasswordHash(null);
        return u;
    }

    @PostMapping
    public User create(@Valid @RequestBody CreateUserReq req) {
        User u = new User();
        u.setUsername(req.getUsername());
        u.setDisplayName(req.getDisplayName());
        u.setRole(req.getRole());
        u.setSkillsJson(req.getSkillsJson());
        u.setBaseCapacity(req.getBaseCapacity());
        u.setAvgPerformance(req.getAvgPerformance());
        User out = userAdminService.create(u, req.getPassword(), SecurityUtils.requireUser().getId());
        out.setPasswordHash(null);
        return out;
    }

    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User patch) {
        patch.setId(id);
        User out = userAdminService.update(patch, SecurityUtils.requireUser().getId());
        out.setPasswordHash(null);
        return out;
    }

    @PostMapping("/{id}/password")
    public void password(@PathVariable Long id, @RequestBody PasswordReq req) {
        userAdminService.resetPassword(id, req.getPassword(), SecurityUtils.requireUser().getId());
    }

    @PostMapping("/{id}/disable")
    public User disable(@PathVariable Long id) {
        User out = userAdminService.setStatus(id, 0, SecurityUtils.requireUser().getId());
        out.setPasswordHash(null);
        return out;
    }

    @PostMapping("/{id}/enable")
    public User enable(@PathVariable Long id) {
        User out = userAdminService.setStatus(id, 1, SecurityUtils.requireUser().getId());
        out.setPasswordHash(null);
        return out;
    }

    @Data
    public static class CreateUserReq {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
        private String displayName;
        @NotBlank
        private String role;
        private String skillsJson;
        private java.math.BigDecimal baseCapacity;
        private java.math.BigDecimal avgPerformance;
    }

    @Data
    public static class PasswordReq {
        @NotBlank
        private String password;
    }
}
