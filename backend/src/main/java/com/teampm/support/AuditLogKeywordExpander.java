package com.teampm.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 将界面上的中文动作/资源描述与英文 code 对齐，供审计日志搜索扩展关键词。
 * 需与 {@code frontend/src/constants/auditLabels.ts} 保持同步。
 */
public final class AuditLogKeywordExpander {

    private static final Map<String, String> ACTION_ZH = new LinkedHashMap<>();

    private static final Map<String, String> RESOURCE_ZH = new LinkedHashMap<>();

    static {
        ACTION_ZH.put("USER_CREATE", "创建用户");
        ACTION_ZH.put("USER_UPDATE", "更新用户");
        ACTION_ZH.put("USER_PASSWORD_RESET", "重置密码");
        ACTION_ZH.put("USER_DISABLE", "禁用用户");
        ACTION_ZH.put("USER_ENABLE", "启用用户");
        ACTION_ZH.put("TEAM_CREATE", "创建团队");
        ACTION_ZH.put("TEAM_UPDATE", "更新团队");
        ACTION_ZH.put("TEAM_ARCHIVE", "归档团队");
        ACTION_ZH.put("TEAM_DELETE", "删除团队");
        ACTION_ZH.put("TEAM_MEMBER_ADD", "添加成员");
        ACTION_ZH.put("TEAM_MEMBER_REMOVE", "移除成员");
        ACTION_ZH.put("TEAM_MEMBER_APPROVE", "通过成员");
        ACTION_ZH.put("TEAM_MEMBER_REJECT", "拒绝成员");
        ACTION_ZH.put("TASK_CREATE", "创建任务");
        ACTION_ZH.put("TASK_UPDATE", "更新任务");
        ACTION_ZH.put("TASK_STATUS_APPLY", "申请状态变更");
        ACTION_ZH.put("TASK_REASSIGN", "改派任务");
        ACTION_ZH.put("PERF_CYCLE_OPEN", "开启绩效周期");
        ACTION_ZH.put("PERF_CYCLE_CLOSE", "关闭绩效周期");
        ACTION_ZH.put("SYSTEM_CONFIG", "系统配置");
        ACTION_ZH.put("GLOBAL_FCE_AHP_SAVE", "保存全局FCE层次分析权重");

        RESOURCE_ZH.put("User", "用户");
        RESOURCE_ZH.put("Team", "团队");
        RESOURCE_ZH.put("TeamMember", "团队成员");
        RESOURCE_ZH.put("Task", "任务");
        RESOURCE_ZH.put("PerformanceCycle", "绩效周期");
        RESOURCE_ZH.put("SystemConfig", "系统配置");
        RESOURCE_ZH.put("GlobalFceAhp", "全局FCE层次分析");
    }

    private AuditLogKeywordExpander() {
    }

    /**
     * 返回去重后的搜索词列表，始终包含用户原始关键词；若中文/英文命中映射则追加对应 code。
     */
    public static List<String> expand(String rawKeyword) {
        Objects.requireNonNull(rawKeyword, "rawKeyword");
        String q = rawKeyword.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, Boolean> ordered = new LinkedHashMap<>();
        ordered.put(q, true);
        String qLower = q.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : ACTION_ZH.entrySet()) {
            String code = e.getKey();
            String label = e.getValue();
            if (label.contains(q) || code.toLowerCase(Locale.ROOT).contains(qLower)) {
                ordered.put(code, true);
            }
        }
        for (Map.Entry<String, String> e : RESOURCE_ZH.entrySet()) {
            String type = e.getKey();
            String label = e.getValue();
            if (label.contains(q) || type.toLowerCase(Locale.ROOT).contains(qLower)) {
                ordered.put(type, true);
            }
        }
        return new ArrayList<>(ordered.keySet());
    }
}
