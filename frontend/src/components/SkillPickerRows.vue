<template>
  <div>
    <a-alert type="info" show-icon style="margin-bottom: 12px">
      <template #message>熟练度（0~1）与匹配算法</template>
      <template #description>
        <p style="margin: 0 0 8px">
          请填写<strong>具体数值</strong>（0~1），用于与任务需求技能的向量做余弦相似度等计算；数值越接近需求，匹配度越高。
        </p>
        <ul class="hint-list">
          <li v-for="h in SKILL_PROFICIENCY_HINTS" :key="h.range">
            <strong>{{ h.range }}</strong>（{{ h.title }}）：{{ h.desc }}
          </li>
        </ul>
      </template>
    </a-alert>
    <div v-for="(row, i) in rows" :key="i" class="row">
      <a-select
        v-model:value="row.skillCode"
        allow-clear
        show-search
        :options="optionsForRow(row)"
        :filter-option="filterOption"
        placeholder="选择技能"
        style="width: 38%; min-width: 140px"
      />
      <div class="num-wrap">
        <a-input-number
          v-model:value="row.proficiency"
          :min="0"
          :max="1"
          :step="0.01"
          placeholder="0~1"
          style="width: 140px"
          @blur="() => normalizeRow(row)"
        />
        <span class="num-hint">精确值 · 0~1</span>
      </div>
      <a-button type="link" danger @click="remove(i)">删除</a-button>
    </div>
    <a-button type="dashed" block style="margin-top: 8px" @click="addRow">添加技能</a-button>
  </div>
</template>

<script setup lang="ts">
import { SKILL_PROFICIENCY_HINTS, clampProficiency, defaultProficiency, type SkillCatalogEntry, type SkillRow } from "../constants/skillTiers";

const props = defineProps<{
  catalog: SkillCatalogEntry[];
}>();

const rows = defineModel<SkillRow[]>("rows", { required: true });

function normalizeRow(row: SkillRow) {
  if (row.proficiency == null) {
    row.proficiency = defaultProficiency();
    return;
  }
  row.proficiency = clampProficiency(Number(row.proficiency));
}

function takenCodes(except?: string) {
  const s = new Set<string>();
  for (const r of rows.value) {
    const c = r.skillCode?.trim();
    if (c && c !== except) s.add(c);
  }
  return s;
}

function optionsForRow(row: SkillRow) {
  const taken = takenCodes(row.skillCode?.trim());
  return props.catalog
    .filter((c) => !taken.has(c.skillCode))
    .map((c) => ({
      value: c.skillCode,
      label: `${c.label}（${c.skillCode}）`,
    }));
}

function filterOption(input: string, option: { label?: string }) {
  return (option.label ?? "").toLowerCase().includes(input.toLowerCase());
}

function addRow() {
  rows.value = [...rows.value, { skillCode: undefined, proficiency: defaultProficiency() }];
}

function remove(i: number) {
  rows.value = rows.value.filter((_, j) => j !== i);
}
</script>

<style scoped>
.row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.num-wrap {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 140px;
}
.num-hint {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
.hint-list {
  margin: 0;
  padding-left: 1.25em;
}
</style>
