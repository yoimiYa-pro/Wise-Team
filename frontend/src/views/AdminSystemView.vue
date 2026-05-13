<template>
  <div v-if="role !== 'ADMIN'"></div>
  <a-space v-else direction="vertical" size="large" style="width: 100%">
    <a-card>
      <a-typography-text type="secondary" style="display: block; margin-bottom: 12px">
        平滑系数与 FCE 标量权重为系统级参数；修改将影响绩效与风险相关计算，请谨慎操作。
      </a-typography-text>
      <a-alert
        type="info"
        show-icon
        style="margin-bottom: 16px"
        message="FCE 权重优先级"
        description="若已在下方「全局 FCE 维度 AHP」保存通过一致性检验的矩阵，绩效关账将优先使用 AHP 权向量；否则使用本页下列三项 fce.weights.*（保存后归一化）。"
      />
      <a-form :model="cfg" layout="vertical" @finish="onSave">
        <a-form-item
          v-for="k in configFormKeys"
          :key="k"
          :name="k"
          :extra="paramHelp(k) || undefined"
        >
          <template #label>
            <div class="param-label">
              <div class="param-title">{{ paramLabel(k) }}</div>
              <div class="param-key-line">{{ k }}</div>
            </div>
          </template>
          <a-input v-model:value="cfg[k]" :placeholder="paramPlaceholder(k)" />
        </a-form-item>
        <a-button type="primary" html-type="submit">保存系统参数</a-button>
      </a-form>
    </a-card>

    <a-card title="全局 FCE 维度 AHP">
      <a-typography-paragraph type="secondary">
        准则顺序为：团队管理者评价、系统指标、同事互评（与关账时 FCE 矩阵行顺序一致）。使用 1–9 标度填写两两比较，保存前须通过一致性检验（CR &lt; 0.1）。
      </a-typography-paragraph>
      <a-typography-text
        v-if="fceAhpLoadedFromServer"
        type="secondary"
        style="display: block; margin-bottom: 8px"
      >
        已从服务器加载上次保存的判断矩阵，可直接预览或修改后再保存。
      </a-typography-text>
      <a-form layout="inline" :model="fceMform" @finish="onFcePreview">
        <a-space direction="vertical">
          <a-space>
            <span>管理者 vs 系统</span>
            <a-form-item name="a12">
              <a-input-number v-model:value="fceMform.a12" :min="0.11" :max="9" :step="0.1" />
            </a-form-item>
          </a-space>
          <a-space>
            <span>管理者 vs 互评</span>
            <a-form-item name="a13">
              <a-input-number v-model:value="fceMform.a13" :min="0.11" :max="9" :step="0.1" />
            </a-form-item>
          </a-space>
          <a-space>
            <span>系统 vs 互评</span>
            <a-form-item name="a23">
              <a-input-number v-model:value="fceMform.a23" :min="0.11" :max="9" :step="0.1" />
            </a-form-item>
          </a-space>
          <a-space>
            <a-button html-type="submit">预览</a-button>
            <a-button type="primary" ghost @click="onFceSaveCurrent">保存当前矩阵</a-button>
            <a-button type="primary" @click="onFceSaveExample">使用示例矩阵保存</a-button>
          </a-space>
        </a-space>
      </a-form>
      <div v-if="fcePreview" class="ahp-preview">
        <a-divider orientation="left" style="margin-top: 8px">预览结果</a-divider>
        <a-alert
          :type="fcePreviewConsistent ? 'success' : 'warning'"
          :message="
            fcePreviewConsistent
              ? '一致性检验通过（CR &lt; 0.1），可将当前矩阵保存为全局 FCE 权重'
              : '一致性未通过，请调整两两比值后再次预览'
          "
          show-icon
          style="margin-bottom: 12px"
        />
        <a-table
          size="small"
          :columns="fcePreviewTableColumns"
          :data-source="fcePreviewWeightRows"
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
            {{ fmtFcePreviewMetric(fcePreview, "lambdaMax") }}
          </a-descriptions-item>
          <a-descriptions-item label="CI（一致性指标）">
            {{ fmtFcePreviewMetric(fcePreview, "ci") }}
          </a-descriptions-item>
          <a-descriptions-item label="CR（一致性比例）">
            {{ fmtFcePreviewMetric(fcePreview, "cr") }}
          </a-descriptions-item>
          <a-descriptions-item label="阈值">CR &lt; 0.1 为通过</a-descriptions-item>
        </a-descriptions>
      </div>
    </a-card>
  </a-space>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { message } from "ant-design-vue";
import client from "../api/client";

const router = useRouter();
const role = ref(localStorage.getItem("role") || "");
const cfg = reactive<Record<string, string>>({});

/** 与下方 AHP 维度顺序一致：管理者、系统、互评；平滑系数放最后便于对照 */
const configFormKeys = [
  "fce.weights.manager",
  "fce.weights.system",
  "fce.weights.peer",
  "load.smoothing.alpha",
] as const;

type GlobalFceAhpRow = {
  matrixJson?: string;
  weightsJson?: string;
  crValue?: number;
  consistentFlag?: number;
};

const FCE_DIMENSION_LABELS = ["团队管理者评价", "系统指标", "同事互评"];

const fceMform = reactive({ a12: 3, a13: 5, a23: 2 });
const fcePreview = ref<Record<string, unknown> | null>(null);
const fceAhpLoadedFromServer = ref(false);

const fcePreviewTableColumns = [
  { title: "维度", dataIndex: "label", key: "label", width: 140 },
  { title: "权重（小数）", dataIndex: "wText", key: "wText", width: 130 },
  { title: "占比", key: "bar", width: 220 },
];

const fcePreviewConsistent = computed(() => Boolean(fcePreview.value?.consistent));

const fcePreviewWeightRows = computed(() => {
  const p = fcePreview.value;
  if (!p || !Array.isArray(p.weights)) return [];
  return (p.weights as unknown[]).map((x, i) => {
    const w = Number(x);
    const ok = Number.isFinite(w);
    const pctNum = ok ? Math.min(100, Math.max(0, Math.round(w * 1000) / 10)) : 0;
    const pctText = ok ? `${(w * 100).toFixed(1)}%` : "—";
    return {
      key: i,
      label: FCE_DIMENSION_LABELS[i] ?? `维度 ${i + 1}`,
      wText: ok ? w.toFixed(4) : "—",
      pctNum,
      pctText,
    };
  });
});

const defaultFceMatrix = [
  [1, 3, 5],
  [1 / 3, 1, 2],
  [1 / 5, 1 / 2, 1],
];

/** 与 backend SystemConfigService 默认键一致；未知键仅显示英文键名 */
const PARAM_INFO: Record<string, { title: string; help: string; placeholder: string }> = {
  "fce.weights.manager": {
    title: "团队管理者评价权重（回落值）",
    help: "与下方 AHP 第一维对应。保存全局 FCE-AHP 后会自动与权向量对齐写入系统参数；未配置 AHP 时关账用此项。",
    placeholder: "0～1，如 0.4",
  },
  "fce.weights.system": {
    title: "系统指标权重（回落值）",
    help: "与下方 AHP 第二维对应。保存全局 FCE-AHP 后会自动与权向量对齐写入系统参数。",
    placeholder: "0～1，如 0.35",
  },
  "fce.weights.peer": {
    title: "同事互评权重（回落值）",
    help: "与下方 AHP 第三维对应。保存全局 FCE-AHP 后会自动与权向量对齐写入系统参数。",
    placeholder: "0～1，如 0.25",
  },
  "load.smoothing.alpha": {
    title: "负荷平滑系数 α",
    help: "对周负荷等序列做指数平滑时，当前观测的权重。越大越重视最近一周、波动更明显；越小曲线越平。常取 0.2～0.6。",
    placeholder: "0～1 之间，如 0.4",
  },
};

function paramLabel(k: string) {
  return PARAM_INFO[k]?.title ?? k;
}

function paramHelp(k: string) {
  return PARAM_INFO[k]?.help ?? "";
}

function paramPlaceholder(k: string) {
  return PARAM_INFO[k]?.placeholder ?? "配置值";
}

function fmtFcePreviewMetric(p: Record<string, unknown>, key: string): string {
  const v = p[key];
  if (v == null) return "—";
  const n = Number(v);
  return Number.isFinite(n) ? n.toFixed(6) : "—";
}

function applyFceMatrixToMform(m: number[][]): boolean {
  if (!m || m.length < 3) return false;
  const r0 = m[0];
  const r1 = m[1];
  if (!r0 || !r1 || r0.length < 3 || r1.length < 3) return false;
  const a12 = Number(r0[1]);
  const a13 = Number(r0[2]);
  const a23 = Number(r1[2]);
  if (![a12, a13, a23].every((x) => Number.isFinite(x) && x > 0)) return false;
  fceMform.a12 = a12;
  fceMform.a13 = a13;
  fceMform.a23 = a23;
  return true;
}

function buildFceMatrixFromMform(): number[][] {
  const v = fceMform;
  return [
    [1, v.a12, v.a13],
    [1 / v.a12, 1, v.a23],
    [1 / v.a13, 1 / v.a23, 1],
  ];
}

/** 将全局 FCE AHP 权向量 [管理者,系统,互评] 写入上方三项回落值（仅展示与本地 cfg） */
function applyWeightsJsonToCfg(weightsJson: string | undefined) {
  if (weightsJson == null || weightsJson === "") return;
  try {
    const w = JSON.parse(weightsJson) as unknown;
    if (!Array.isArray(w) || w.length !== 3) return;
    const a = Number(w[0]);
    const b = Number(w[1]);
    const c = Number(w[2]);
    if (![a, b, c].every((x) => Number.isFinite(x))) return;
    cfg["fce.weights.manager"] = a.toFixed(4);
    cfg["fce.weights.system"] = b.toFixed(4);
    cfg["fce.weights.peer"] = c.toFixed(4);
  } catch {
    /* ignore */
  }
}

/** 保存 AHP 后把回落权重写回 system_config，与界面一致 */
async function persistCfgAfterFceWeightsSync() {
  await client.put("/admin/system/config", { ...cfg });
}

async function loadSavedFceAhp() {
  fceAhpLoadedFromServer.value = false;
  try {
    const { data } = await client.get<GlobalFceAhpRow | null>("/admin/system/fce-ahp");
    if (data == null) return;
    if (data.consistentFlag === 1 && data.weightsJson) {
      applyWeightsJsonToCfg(data.weightsJson);
    }
    if (!data.matrixJson) return;
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
    if (applyFceMatrixToMform(rows)) {
      fceAhpLoadedFromServer.value = true;
    }
  } catch {
    /* 忽略 */
  }
}

onMounted(async () => {
  if (role.value !== "ADMIN") {
    router.replace("/");
    return;
  }
  const { data } = await client.get<Record<string, string>>("/admin/system/config");
  Object.keys(data).forEach((k) => {
    cfg[k] = data[k];
  });
  await loadSavedFceAhp();
  // 若库中已有通过一致性的全局 FCE-AHP，回落权重展示与权向量对齐（不写库，避免误覆盖）
});

async function onSave() {
  await client.put("/admin/system/config", { ...cfg });
  message.success("已保存系统参数");
}

async function onFcePreview() {
  const matrix = buildFceMatrixFromMform();
  const { data } = await client.post("/admin/system/fce-ahp/preview", { matrix });
  fcePreview.value = data;
  message.info(data.consistent ? "一致性通过" : "一致性未通过");
}

async function onFceSaveExample() {
  const { data: saved } = await client.put("/admin/system/fce-ahp", { matrix: defaultFceMatrix });
  applyFceMatrixToMform(defaultFceMatrix);
  applyWeightsJsonToCfg(saved?.weightsJson);
  await persistCfgAfterFceWeightsSync();
  const { data: pv } = await client.post("/admin/system/fce-ahp/preview", { matrix: defaultFceMatrix });
  fcePreview.value = pv;
  message.success("已保存全局 FCE 权重，并已同步上方回落权重");
  fceAhpLoadedFromServer.value = true;
}

async function onFceSaveCurrent() {
  const matrix = buildFceMatrixFromMform();
  const { data } = await client.post("/admin/system/fce-ahp/preview", { matrix });
  fcePreview.value = data;
  if (!data.consistent) {
    message.warning("一致性未通过，无法保存");
    return;
  }
  const { data: saved } = await client.put("/admin/system/fce-ahp", { matrix });
  applyWeightsJsonToCfg(saved?.weightsJson);
  await persistCfgAfterFceWeightsSync();
  message.success("已保存全局 FCE 权重，并已同步上方回落权重");
  fceAhpLoadedFromServer.value = true;
}
</script>

<style scoped>
.param-label {
  line-height: 1.45;
}
.param-title {
  font-weight: 500;
}
.param-key-line {
  margin-top: 2px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
  font-weight: normal;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
.ahp-preview {
  margin-top: 8px;
}
</style>
