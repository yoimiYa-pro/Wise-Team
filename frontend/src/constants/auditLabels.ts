/** 与后端 AuditLogKeywordExpander 保持同步（审计搜索按中文扩展关键词）。 */
const ACTION_LABELS: Record<string, string> = {
  USER_CREATE: "创建用户",
  USER_UPDATE: "更新用户",
  USER_PASSWORD_RESET: "重置密码",
  USER_DISABLE: "禁用用户",
  USER_ENABLE: "启用用户",
  TEAM_CREATE: "创建团队",
  TEAM_UPDATE: "更新团队",
  TEAM_ARCHIVE: "归档团队",
  TEAM_DELETE: "删除团队",
  TEAM_MEMBER_ADD: "添加成员",
  TEAM_MEMBER_REMOVE: "移除成员",
  TEAM_MEMBER_APPROVE: "通过成员",
  TEAM_MEMBER_REJECT: "拒绝成员",
  TASK_CREATE: "创建任务",
  TASK_UPDATE: "更新任务",
  TASK_STATUS_APPLY: "申请状态变更",
  TASK_REASSIGN: "改派任务",
  PERF_CYCLE_OPEN: "开启绩效周期",
  PERF_CYCLE_CLOSE: "关闭绩效周期",
  SYSTEM_CONFIG: "系统配置",
  GLOBAL_FCE_AHP_SAVE: "保存全局 FCE 层次分析权重",
};

const RESOURCE_LABELS: Record<string, string> = {
  User: "用户",
  Team: "团队",
  TeamMember: "团队成员",
  Task: "任务",
  PerformanceCycle: "绩效周期",
  SystemConfig: "系统配置",
  GlobalFceAhp: "全局 FCE 层次分析",
};

export function actionLabel(code: string): string {
  return ACTION_LABELS[code] ?? code;
}

export function resourceLabel(type: string | undefined): string {
  if (type == null || type === "") {
    return "—";
  }
  return RESOURCE_LABELS[type] ?? type;
}
