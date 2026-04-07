package com.teampm.service;

import com.teampm.domain.SkillOption;
import com.teampm.domain.Team;
import com.teampm.exception.ApiException;
import com.teampm.mapper.SkillOptionMapper;
import com.teampm.security.UserPrincipal;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SkillOptionService {

    public static final Long GLOBAL_TEAM_ID = 0L;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    private final SkillOptionMapper skillOptionMapper;
    private final TeamService teamService;

    public List<SkillOption> listGlobal() {
        return skillOptionMapper.findByTeamId(GLOBAL_TEAM_ID);
    }

    public List<SkillOption> listForTeam(Long teamId) {
        teamService.requireTeam(teamId);
        return skillOptionMapper.findByTeamId(teamId);
    }

    /**
     * 合并全局与所有团队可选项：同 skillCode 时团队条目的展示名覆盖全局；排序优先全局顺序，其余按团队条目顺序。
     */
    public List<CatalogItem> mergedCatalog() {
        List<SkillOption> global = skillOptionMapper.findByTeamId(GLOBAL_TEAM_ID);
        List<SkillOption> teamRows = skillOptionMapper.findAllTeamScoped();
        Map<String, Acc> byCode = new LinkedHashMap<>();
        for (SkillOption g : global) {
            String c = normalizeCode(g.getSkillCode());
            int so = g.getSortOrder() != null ? g.getSortOrder() : 0;
            byCode.put(c, new Acc(c, g.getLabel(), so, 0));
        }
        int seq = 0;
        for (SkillOption t : teamRows) {
            String c = normalizeCode(t.getSkillCode());
            int so = t.getSortOrder() != null ? t.getSortOrder() : 0;
            Acc cur = byCode.get(c);
            if (cur == null) {
                byCode.put(c, new Acc(c, t.getLabel(), 10_000 + so, ++seq));
            } else {
                byCode.put(c, new Acc(c, t.getLabel(), cur.sortPrimary, cur.sortSecondary));
            }
        }
        List<CatalogItem> out = new ArrayList<>();
        for (Acc a : byCode.values()) {
            out.add(new CatalogItem(a.code, a.label, a.sortPrimary, a.sortSecondary));
        }
        out.sort(Comparator.comparingInt(CatalogItem::getSortPrimary).thenComparingInt(CatalogItem::getSortSecondary).thenComparing(CatalogItem::getSkillCode));
        return out;
    }

    @Transactional
    public SkillOption createGlobal(String skillCode, String label, Integer sortOrder, UserPrincipal actor) {
        requireAdmin(actor);
        return insertChecked(GLOBAL_TEAM_ID, skillCode, label, sortOrder);
    }

    @Transactional
    public SkillOption updateGlobal(Long id, String skillCode, String label, Integer sortOrder, UserPrincipal actor) {
        requireAdmin(actor);
        SkillOption row = requireRow(id);
        if (!GLOBAL_TEAM_ID.equals(row.getTeamId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "非全局技能项");
        }
        applyUpdate(row, skillCode, label, sortOrder);
        skillOptionMapper.update(row);
        return skillOptionMapper.findById(id);
    }

    @Transactional
    public void deleteGlobal(Long id, UserPrincipal actor) {
        requireAdmin(actor);
        SkillOption row = requireRow(id);
        if (!GLOBAL_TEAM_ID.equals(row.getTeamId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "非全局技能项");
        }
        skillOptionMapper.deleteById(id);
    }

    @Transactional
    public SkillOption createTeam(Long teamId, String skillCode, String label, Integer sortOrder, UserPrincipal actor) {
        Team team = teamService.requireTeam(teamId);
        teamService.assertManager(actor, team);
        return insertChecked(teamId, skillCode, label, sortOrder);
    }

    @Transactional
    public SkillOption updateTeam(Long teamId, Long id, String skillCode, String label, Integer sortOrder, UserPrincipal actor) {
        Team team = teamService.requireTeam(teamId);
        teamService.assertManager(actor, team);
        SkillOption row = requireRow(id);
        if (!teamId.equals(row.getTeamId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "技能项不属于该团队");
        }
        applyUpdate(row, skillCode, label, sortOrder);
        skillOptionMapper.update(row);
        return skillOptionMapper.findById(id);
    }

    @Transactional
    public void deleteTeam(Long teamId, Long id, UserPrincipal actor) {
        Team team = teamService.requireTeam(teamId);
        teamService.assertManager(actor, team);
        SkillOption row = requireRow(id);
        if (!teamId.equals(row.getTeamId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "技能项不属于该团队");
        }
        skillOptionMapper.deleteById(id);
    }

    private SkillOption insertChecked(Long teamId, String skillCode, String label, Integer sortOrder) {
        String code = validateCode(skillCode);
        if (label == null || label.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "展示名不能为空");
        }
        SkillOption row = new SkillOption();
        row.setTeamId(teamId);
        row.setSkillCode(code);
        row.setLabel(label.trim());
        row.setSortOrder(sortOrder != null ? sortOrder : 0);
        skillOptionMapper.insert(row);
        return skillOptionMapper.findById(row.getId());
    }

    private void applyUpdate(SkillOption row, String skillCode, String label, Integer sortOrder) {
        row.setSkillCode(validateCode(skillCode));
        if (label == null || label.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "展示名不能为空");
        }
        row.setLabel(label.trim());
        row.setSortOrder(sortOrder != null ? sortOrder : 0);
    }

    private SkillOption requireRow(Long id) {
        SkillOption row = skillOptionMapper.findById(id);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "技能项不存在");
        }
        return row;
    }

    private static void requireAdmin(UserPrincipal actor) {
        if (!"ADMIN".equals(actor.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "仅管理员可操作全局技能");
        }
    }

    private static String validateCode(String skillCode) {
        if (skillCode == null || skillCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "技能代码不能为空");
        }
        String c = skillCode.trim().toLowerCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(c).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "技能代码须为小写字母开头，仅含小写字母、数字、下划线，最长 64 字符");
        }
        return c;
    }

    private static String normalizeCode(String skillCode) {
        if (skillCode == null || skillCode.isBlank()) {
            return "";
        }
        return skillCode.trim().toLowerCase(Locale.ROOT);
    }

    @Data
    public static class CatalogItem {
        private final String skillCode;
        private final String label;
        private final int sortPrimary;
        private final int sortSecondary;
    }

    private record Acc(String code, String label, int sortPrimary, int sortSecondary) {
    }
}
