package com.teampm.service;

import com.teampm.domain.User;
import com.teampm.domain.UserProfileRequest;
import com.teampm.dto.ProfileRequestView;
import com.teampm.exception.ApiException;
import com.teampm.mapper.UserMapper;
import com.teampm.mapper.UserProfileRequestMapper;
import com.teampm.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserProfileRequestService {

    public static final String REF_TYPE = "USER_PROFILE_REQUEST";
    public static final String MSG_APPLY = "USER_PROFILE_APPLY";
    public static final String MSG_APPROVED = "USER_PROFILE_APPROVED";
    public static final String MSG_REJECTED = "USER_PROFILE_REJECTED";

    private final UserProfileRequestMapper requestMapper;
    private final UserMapper userMapper;
    private final InAppMessageService messageService;
    private final ObjectMapper objectMapper;

    @Transactional
    public UserProfileRequest submit(SubmitBody body, UserPrincipal actor) {
        if ("ADMIN".equals(actor.getRole())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "管理员请在「用户管理」中直接修改用户资料");
        }
        Long uid = actor.getId();
        User user = userMapper.findById(uid);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "账号不可用");
        }
        if (requestMapper.findPendingByUserId(uid) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "您已有待审核的个人信息申请，请等待处理后再提交");
        }

        String displayName = trimOrEmpty(body.getDisplayName());
        if (displayName.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "显示名不能为空");
        }
        if (displayName.length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "显示名过长");
        }

        String skillsJson = normalizeSkillsJson(body.getSkillsJson());
        BigDecimal cap = body.getBaseCapacity();
        if (cap == null || cap.compareTo(BigDecimal.valueOf(0.5)) < 0 || cap.compareTo(BigDecimal.valueOf(168)) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "额定周工时须在 0.5～168 之间");
        }

        if (!profileDiffersFrom(user, displayName, skillsJson, cap)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "未修改任何资料，无需提交申请");
        }

        UserProfileRequest r = new UserProfileRequest();
        r.setUserId(uid);
        r.setProposedDisplayName(displayName);
        r.setProposedSkillsJson(skillsJson);
        r.setProposedBaseCapacity(cap);
        r.setApplyReason(trimToNull(body.getApplyReason()));
        r.setReviewStatus("PENDING");
        requestMapper.insert(r);
        Long rid = r.getId();

        User applicant = userMapper.findById(uid);
        String an = applicant != null && applicant.getUsername() != null ? applicant.getUsername() : "?";
        String title = "【待审批】个人信息变更";
        String msg = String.format(
                "用户 %s 申请修改个人资料（显示名、技能、额定工时）。%s",
                an,
                r.getApplyReason() != null ? "说明：" + r.getApplyReason() : ""
        );
        messageService.sendToMany(collectActiveAdminIds(), title, msg, MSG_APPLY, REF_TYPE, rid);
        return Objects.requireNonNull(requestMapper.findById(rid));
    }

    public UserProfileRequest myPending(UserPrincipal actor) {
        return requestMapper.findPendingByUserId(actor.getId());
    }

    public List<ProfileRequestView> listPendingAdmin(UserPrincipal actor) {
        assertAdmin(actor);
        return enrich(requestMapper.findAllPending());
    }

    @Transactional
    public void approve(Long requestId, String reviewComment, UserPrincipal actor) {
        assertAdmin(actor);
        UserProfileRequest r = requirePending(requestId);
        User u = userMapper.findById(r.getUserId());
        if (u == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        u.setDisplayName(r.getProposedDisplayName());
        u.setSkillsJson(r.getProposedSkillsJson());
        u.setBaseCapacity(r.getProposedBaseCapacity());
        userMapper.update(u);

        r.setReviewStatus("APPROVED");
        r.setReviewerId(actor.getId());
        r.setReviewComment(trimToNull(reviewComment));
        requestMapper.updateReview(r);

        String title = "【已通过】个人信息变更";
        String msg = String.format(
                "您申请修改的个人资料已由管理员审核通过。%s",
                r.getReviewComment() != null ? "备注：" + r.getReviewComment() : ""
        );
        messageService.send(r.getUserId(), title, msg, MSG_APPROVED, REF_TYPE, requestId);
    }

    @Transactional
    public void reject(Long requestId, String reviewComment, UserPrincipal actor) {
        if (reviewComment == null || reviewComment.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "驳回须填写原因");
        }
        assertAdmin(actor);
        UserProfileRequest r = requirePending(requestId);
        r.setReviewStatus("REJECTED");
        r.setReviewerId(actor.getId());
        r.setReviewComment(reviewComment.trim());
        requestMapper.updateReview(r);

        String title = "【已驳回】个人信息变更";
        String msg = String.format(
                "您申请修改的个人资料未通过审核。\n驳回原因：%s",
                r.getReviewComment()
        );
        messageService.send(r.getUserId(), title, msg, MSG_REJECTED, REF_TYPE, requestId);
    }

    private UserProfileRequest requirePending(Long id) {
        UserProfileRequest r = requestMapper.findById(id);
        if (r == null || !"PENDING".equals(r.getReviewStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "申请不存在或已处理");
        }
        return r;
    }

    private void assertAdmin(UserPrincipal actor) {
        if (!"ADMIN".equals(actor.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "仅系统管理员可审批个人信息变更");
        }
    }

    private List<Long> collectActiveAdminIds() {
        List<Long> ids = new ArrayList<>();
        for (User u : userMapper.findAll()) {
            if ("ADMIN".equals(u.getRole()) && u.getStatus() != null && u.getStatus() != 0) {
                ids.add(u.getId());
            }
        }
        return ids;
    }

    private List<ProfileRequestView> enrich(List<UserProfileRequest> list) {
        List<ProfileRequestView> out = new ArrayList<>(list.size());
        for (UserProfileRequest r : list) {
            ProfileRequestView v = new ProfileRequestView();
            v.setRequest(r);
            User u = userMapper.findById(r.getUserId());
            v.setApplicantUsername(u != null ? u.getUsername() : "-");
            if (u != null) {
                v.setCurrentDisplayName(u.getDisplayName());
                v.setCurrentBaseCapacity(u.getBaseCapacity());
                v.setCurrentSkillsJson(u.getSkillsJson());
            }
            out.add(v);
        }
        return out;
    }

    private boolean profileDiffersFrom(User user, String displayName, String skillsJson, BigDecimal cap) {
        String curDn = user.getDisplayName() != null ? user.getDisplayName().trim() : "";
        if (!curDn.equals(displayName)) {
            return true;
        }
        BigDecimal uCap = user.getBaseCapacity() != null ? user.getBaseCapacity() : BigDecimal.valueOf(40);
        if (uCap.compareTo(cap) != 0) {
            return true;
        }
        return !skillsJsonEquals(user.getSkillsJson(), skillsJson);
    }

    private boolean skillsJsonEquals(String a, String b) {
        try {
            JsonNode ja = objectMapper.readTree(normalizeSkillsJsonSafe(a));
            JsonNode jb = objectMapper.readTree(normalizeSkillsJsonSafe(b));
            return ja.equals(jb);
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /** 库内旧数据可能非严格 JSON，比较用，不抛业务异常 */
    private String normalizeSkillsJsonSafe(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        try {
            JsonNode n = objectMapper.readTree(raw);
            if (!n.isObject()) {
                return "{}";
            }
            return objectMapper.writeValueAsString(n);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String normalizeSkillsJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        try {
            JsonNode n = objectMapper.readTree(raw);
            if (!n.isObject()) {
                return "{}";
            }
            return objectMapper.writeValueAsString(n);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "技能数据 JSON 格式无效");
        }
    }

    private static String trimOrEmpty(String s) {
        if (s == null) {
            return "";
        }
        return s.trim();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Data
    public static class SubmitBody {
        private String displayName;
        private String skillsJson;
        private BigDecimal baseCapacity;
        private String applyReason;
    }

    @Data
    public static class ReviewBody {
        private String reviewComment;
    }
}
