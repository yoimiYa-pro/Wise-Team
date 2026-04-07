/** 技能下拉用的合并目录项（与 /api/skill-options/catalog 一致） */
export type SkillCatalogEntry = { skillCode: string; label: string };

/**
 * 熟练度量化参考（0~1，与后端 SkillMatch 一致）。
 * 实际提交为具体数值，不限于下列区间边界。
 */
export const SKILL_PROFICIENCY_HINTS = [
  { range: "0.1–0.3", title: "入门", desc: "初步掌握，能力较弱" },
  { range: "0.3–0.6", title: "熟练", desc: "小有成就，可以解决基本问题" },
  { range: "0.6–1.0", title: "专家", desc: "专业技术能力，面对复杂问题不在话下" },
] as const;

export type SkillRow = { skillCode?: string; proficiency: number };

export function defaultProficiency(): number {
  return 0.75;
}

/** 将熟练度限制在 [0, 1]，与后端解析一致 */
export function clampProficiency(v: number): number {
  if (Number.isNaN(v)) return 0;
  return Math.max(0, Math.min(1, v));
}

export function buildSkillsJson(rows: SkillRow[]): string {
  const o: Record<string, number> = {};
  for (const r of rows) {
    const c = r.skillCode?.trim();
    if (!c) continue;
    const v = r.proficiency;
    if (typeof v !== "number" || Number.isNaN(v)) continue;
    o[c] = clampProficiency(v);
  }
  return JSON.stringify(o);
}

/** 将用户已存储的 skills_json 解析为编辑行 */
export function parseSkillsJson(json: string | null | undefined): SkillRow[] {
  if (json == null || String(json).trim() === "") return [];
  try {
    const o = JSON.parse(String(json)) as Record<string, unknown>;
    if (o == null || typeof o !== "object" || Array.isArray(o)) return [];
    return Object.entries(o).map(([skillCode, raw]) => {
      const n = typeof raw === "number" ? raw : Number(raw);
      return {
        skillCode,
        proficiency: clampProficiency(Number.isNaN(n) ? 0 : n),
      };
    });
  } catch {
    return [];
  }
}
