/** 与后端 TaskStatusRequestService 允许的流转一致 */

export function canApplyTaskStatus(status: string): boolean {
  return !["COMPLETED", "ARCHIVED"].includes(status);
}

export function nextTaskStatusOptions(from: string): { value: string; label: string }[] {
  switch (from) {
    case "CREATED":
      return [
        { value: "IN_PROGRESS", label: "进行中 (IN_PROGRESS)" },
        { value: "SUSPENDED", label: "暂停 (SUSPENDED)" },
      ];
    case "IN_PROGRESS":
      return [
        { value: "SUSPENDED", label: "暂停 (SUSPENDED)" },
        { value: "COMPLETED", label: "已完成 (COMPLETED)" },
      ];
    case "SUSPENDED":
      return [
        { value: "IN_PROGRESS", label: "进行中 (IN_PROGRESS)" },
        { value: "COMPLETED", label: "已完成 (COMPLETED)" },
      ];
    default:
      return [];
  }
}
