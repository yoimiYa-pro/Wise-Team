<template>
  <a-card :title="`团队 ${tid} — AHP 权重（行/列：技能匹配、负载、绩效）`">
    <a-typography-paragraph type="secondary">
      使用 1–9 标度填写两两比较矩阵，保存前会进行一致性检验（CR &lt; 0.1）。
    </a-typography-paragraph>
    <a-typography-text v-if="savedLoadedFromServer" type="secondary" style="display: block; margin-bottom: 8px">
      已从服务器加载上次保存的判断矩阵，可直接预览或修改后再保存。
    </a-typography-text>
    <a-form layout="inline" :model="mform" @finish="onPreview">
      <a-space direction="vertical">
        <a-space>
          <span>能力 vs 负载</span>
          <a-form-item name="a12"><a-input-number v-model:value="mform.a12" :min="0.11" :max="9" :step="0.1" /></a-form-item>
        </a-space>
        <a-space>
          <span>能力 vs 绩效</span>
          <a-form-item name="a13"><a-input-number v-model:value="mform.a13" :min="0.11" :max="9" :step="0.1" /></a-form-item>
        </a-space>
        <a-space>
          <span>负载 vs 绩效</span>
          <a-form-item name="a23"><a-input-number v-model:value="mform.a23" :min="0.11" :max="9" :step="0.1" /></a-form-item>
        </a-space>
        <a-space>
          <a-button html-type="submit">预览</a-button>
          <a-button type="primary" @click="onSaveDefault">使用示例矩阵保存</a-button>
        </a-space>
      </a-space>
    </a-form>
    <div v-if="preview" class="ahp-preview">
      <a-divider orientation="left" style="margin-top: 8px">预览结果</a-divider>
      <a-alert
        :type="previewConsistent ? 'success' : 'warning'"
        :message="
          previewConsistent
            ? '一致性检验通过（CR &lt; 0.1），可将当前矩阵保存为团队权重'
            : '一致性未通过，请调整两两比值后再次预览'
        "
        show-icon
        style="margin-bottom: 12px"
      />
      <a-table
        size="small"
        :columns="previewTableColumns"
        :data-source="previewWeightRows"
        :pagination="false"
        style="margin-bottom: 12px"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'bar'">
            <a-progress :percent="record.pctNum" size="small" :format="() => record.pctText" />
          </template>
        </template>
      </a-table>
      <a-descriptions size="small" bordered :column="2">
        <a-descriptions-item label="λmax（最大特征根）">
          {{ fmtPreviewMetric(preview, 'lambdaMax') }}
        </a-descriptions-item>
        <a-descriptions-item label="CI（一致性指标）">
          {{ fmtPreviewMetric(preview, 'ci') }}
        </a-descriptions-item>
        <a-descriptions-item label="CR（一致性比例）">
          {{ fmtPreviewMetric(preview, 'cr') }}
        </a-descriptions-item>
        <a-descriptions-item label="阈值">CR &lt; 0.1 为通过</a-descriptions-item>
      </a-descriptions>
    </div>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { message } from "ant-design-vue";
import client from "../api/client";

type TeamAhpRow = {
  matrixJson?: string;
  weightsJson?: string;
  crValue?: number;
};

const route = useRoute();
const tid = computed(() => Number(route.params.teamId));

const mform = reactive({ a12: 3, a13: 5, a23: 2 });
const preview = ref<Record<string, unknown> | null>(null);
const savedLoadedFromServer = ref(false);

const AHP_DIMENSION_LABELS = ["技能匹配", "负载", "绩效"];

const previewTableColumns = [
  { title: "维度", dataIndex: "label", key: "label", width: 120 },
  { title: "权重（小数）", dataIndex: "wText", key: "wText", width: 130 },
  { title: "占比", key: "bar", width: 220 },
];

const previewConsistent = computed(() => Boolean(preview.value?.consistent));

const previewWeightRows = computed(() => {
  const p = preview.value;
  if (!p || !Array.isArray(p.weights)) return [];
  return (p.weights as unknown[]).map((x, i) => {
    const w = Number(x);
    const ok = Number.isFinite(w);
    const pctNum = ok ? Math.min(100, Math.max(0, Math.round(w * 1000) / 10)) : 0;
    const pctText = ok ? `${(w * 100).toFixed(1)}%` : "—";
    return {
      key: i,
      label: AHP_DIMENSION_LABELS[i] ?? `维度 ${i + 1}`,
      wText: ok ? w.toFixed(4) : "—",
      pctNum,
      pctText,
    };
  });
});

function fmtPreviewMetric(p: Record<string, unknown>, key: string): string {
  const v = p[key];
  if (v == null) return "—";
  const n = Number(v);
  return Number.isFinite(n) ? n.toFixed(6) : "—";
}

const defaultMatrix = [
  [1, 3, 5],
  [1 / 3, 1, 2],
  [1 / 5, 1 / 2, 1],
];

function applyMatrixToMform(m: number[][]): boolean {
  if (!m || m.length < 3) return false;
  const r0 = m[0];
  const r1 = m[1];
  if (!r0 || !r1 || r0.length < 3 || r1.length < 3) return false;
  const a12 = Number(r0[1]);
  const a13 = Number(r0[2]);
  const a23 = Number(r1[2]);
  if (![a12, a13, a23].every((x) => Number.isFinite(x) && x > 0)) return false;
  mform.a12 = a12;
  mform.a13 = a13;
  mform.a23 = a23;
  return true;
}

async function loadSavedAhp() {
  savedLoadedFromServer.value = false;
  const id = tid.value;
  if (!id || Number.isNaN(id)) return;
  try {
    const { data } = await client.get<TeamAhpRow | null>(`/teams/${id}/ahp`);
    if (data == null || !data.matrixJson) return;
    let matrix: unknown;
    try {
      matrix = JSON.parse(data.matrixJson) as unknown;
    } catch {
      return;
    }
    if (!Array.isArray(matrix) || matrix.length < 3) return;
    const rows = matrix.map((row) =>
      Array.isArray(row) ? row.map((c) => Number(c)) : []
    ) as number[][];
    if (applyMatrixToMform(rows)) {
      savedLoadedFromServer.value = true;
    }
  } catch {
    /* 非管理者 403 等：保持默认表单 */
  }
}

onMounted(() => {
  void loadSavedAhp();
});

watch(tid, () => {
  void loadSavedAhp();
});

async function onPreview() {
  const v = mform;
  const matrix = [
    [1, v.a12, v.a13],
    [1 / v.a12, 1, v.a23],
    [1 / v.a13, 1 / v.a23, 1],
  ];
  const { data } = await client.post(`/teams/${tid.value}/ahp/preview`, { matrix });
  preview.value = data;
  message.info(data.consistent ? "一致性通过" : "一致性未通过");
}

async function onSaveDefault() {
  await client.post(`/teams/${tid.value}/ahp`, { matrix: defaultMatrix });
  applyMatrixToMform(defaultMatrix);
  message.success("已保存权重");
  savedLoadedFromServer.value = true;
}
</script>

<style scoped>
.ahp-preview {
  margin-top: 8px;
}
</style>
