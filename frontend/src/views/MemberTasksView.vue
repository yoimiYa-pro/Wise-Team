<template>
  <a-card title="我的任务">
    <a-space direction="vertical" style="width: 100%">
      <a-table
        :row-key="(r: TaskRow) => r.id"
        :data-source="tasks"
        :columns="columns"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'risk'">
            <a-tag
              :color="
                record.riskLevel === 'RED' ? 'red' : record.riskLevel === 'ORANGE' ? 'orange' : 'green'
              "
            >
              {{ record.riskLevel }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'progress'">
            <template v-if="record.status === 'COMPLETED'">
              <span>{{ record.progress ?? 100 }}%</span>
            </template>
            <a-space v-else-if="record.status === 'IN_PROGRESS'" align="center" :size="8" wrap>
              <a-slider
                style="width: 220px; margin: 0"
                :value="progressDraft[record.id] ?? record.progress ?? 0"
                :min="0"
                :max="89"
                :tooltip-open="false"
                @change="(v) => onProgressSliderChange(record.id, v)"
                @after-change="() => commitProgressSlider(record)"
              />
              <span class="progress-percent">{{ displayProgressPercent(record) }}%</span>
            </a-space>
            <span v-else class="cell-muted">{{ record.progress ?? 0 }}%（仅进行中可拖拽上报，最高 89%）</span>
          </template>
          <template v-else-if="column.key === 'statusReq'">
            <a-button
              v-if="canApplyForMine(record)"
              type="link"
              size="small"
              @click="openStatusApply(record)"
            >
              申请变更
            </a-button>
            <span v-else class="cell-muted">—</span>
          </template>
        </template>
      </a-table>
      <a-card type="inner" title="本周实际工时（供负载预测）" style="margin-top: 16px">
        <a-form layout="inline" :model="wf" @finish="onLogHours">
          <a-form-item label="团队" name="teamId" :rules="[{ required: true }]">
            <a-select
              v-model:value="wf.teamId"
              style="width: 200px"
              :options="teams.map((t) => ({ label: t.name, value: t.id }))"
            />
          </a-form-item>
          <a-form-item label="小时" name="hours" :rules="[{ required: true }]">
            <a-input-number v-model:value="wf.hours" :min="0" :max="168" />
          </a-form-item>
          <a-button type="primary" html-type="submit">提交</a-button>
        </a-form>
      </a-card>

      <a-card type="inner" title="工时记录与负载预测" style="margin-top: 16px">
        <a-spin :spinning="forecastLoading">
          <a-typography-paragraph type="secondary" style="margin-bottom: 12px; font-size: 13px">
            历史为最近至多 24 周、按您本人汇总的填报记录（含各团队，不按上方下拉过滤）；预测中的「任务排期」为截止日在该周内的任务剩余工时估算。
          </a-typography-paragraph>
          <template v-if="forecast">
            <a-descriptions size="small" :column="2" bordered style="margin-bottom: 16px">
              <a-descriptions-item label="平滑后下周基准（小时）">
                {{ formatHours(forecast.nextWeekSmoothed) }}
              </a-descriptions-item>
              <a-descriptions-item label="平滑系数 α">
                {{ forecast.alpha != null ? String(forecast.alpha) : "—" }}
              </a-descriptions-item>
            </a-descriptions>

            <a-typography-title :level="5">历史周实际工时</a-typography-title>
            <a-table
              v-if="historyRows.length"
              size="small"
              :row-key="(r) => `${r.yearWeek}-${r.teamId ?? ''}`"
              :columns="historyColumns"
              :data-source="historyRows"
              :pagination="false"
              style="margin-bottom: 20px"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'team'">
                  {{ teamLabel(record.teamId) }}
                </template>
                <template v-else-if="column.key === 'actualHours'">
                  {{ formatHours(record.actualHours) }}
                </template>
              </template>
            </a-table>
            <a-empty v-else description="暂无历史填报，提交本周工时后将出现在此处" style="margin-bottom: 20px" />

            <a-typography-title :level="5">未来四周负荷预估</a-typography-title>
            <a-table
              v-if="forecastRows.length"
              size="small"
              :row-key="(r) => r.yearWeek"
              :columns="forecastColumns"
              :data-source="forecastRows"
              :pagination="false"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'smoothedBase'">
                  {{ formatHours(record.smoothedBase) }}
                </template>
                <template v-else-if="column.dataIndex === 'scheduledFromTasks'">
                  {{ formatHours(record.scheduledFromTasks) }}
                </template>
                <template v-else-if="column.dataIndex === 'totalEstimate'">
                  {{ formatHours(record.totalEstimate) }}
                </template>
              </template>
            </a-table>
            <a-empty v-else description="暂无预测数据" />
          </template>
          <a-empty v-else-if="!forecastLoading && wf.teamId == null" description="请先选择团队" />
          <a-empty
            v-else-if="!forecastLoading"
            description="未能加载预测数据，请检查网络或团队权限后重试"
          />
        </a-spin>
      </a-card>
    </a-space>
  </a-card>

  <a-modal
    v-model:open="statusApplyOpen"
    title="申请变更任务状态"
    :confirm-loading="statusApplySaving"
    ok-text="提交"
    cancel-text="取消"
    @ok="submitStatusApply"
    @cancel="statusApplyOpen = false"
  >
    <a-form layout="vertical">
      <a-form-item label="目标状态" required>
        <a-select v-model:value="statusApplyForm.toStatus" :options="statusApplyTargetOptions" style="width: 100%" />
      </a-form-item>
      <a-form-item label="说明（可选）">
        <a-textarea v-model:value="statusApplyForm.applyReason" :rows="3" placeholder="进度与变更依据等" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { message } from "ant-design-vue";
import axios from "axios";
import client from "../api/client";
import { canApplyTaskStatus, nextTaskStatusOptions } from "../constants/taskStatusFlow";

/** ant-design-vue Slider 单柄时值类型；受控模式下须用 @change 同步，否则 @after-change 可能拿到旧值 */
type SliderValue = number | [number, number];

type TaskRow = {
  id: number;
  title: string;
  status: string;
  progress: number;
  version: number;
  riskLevel?: string;
};

type Team = { id: number; name: string };

type WorkloadWeeklyRow = {
  yearWeek?: string;
  teamId?: number;
  actualHours?: number;
  userId?: number;
};

type ForecastWeekRow = {
  yearWeek: string;
  smoothedBase: number;
  scheduledFromTasks: number;
  totalEstimate: number;
};

type ForecastPayload = {
  historyWeeks?: WorkloadWeeklyRow[];
  alpha?: number;
  nextWeekSmoothed?: number;
  forecast?: ForecastWeekRow[];
};

const tasks = ref<TaskRow[]>([]);
/** 拖动中的进度草稿（避免受控 Slider 松手时 afterChange 仍为旧 progress） */
const progressDraft = ref<Record<number, number>>({});
const teams = ref<Team[]>([]);
const wf = reactive<{ teamId?: number; hours: number }>({ hours: 8 });

const forecast = ref<ForecastPayload | null>(null);
const forecastLoading = ref(false);

const historyColumns = [
  { title: "年周", dataIndex: "yearWeek", key: "yearWeek" },
  { title: "团队", key: "team" },
  { title: "实际工时（小时）", key: "actualHours" },
];

const forecastColumns = [
  { title: "年周", dataIndex: "yearWeek", key: "yearWeek" },
  { title: "平滑基准（小时）", dataIndex: "smoothedBase", key: "smoothedBase" },
  { title: "任务排期（小时）", dataIndex: "scheduledFromTasks", key: "scheduledFromTasks" },
  { title: "合计预估（小时）", dataIndex: "totalEstimate", key: "totalEstimate" },
];

const historyRows = computed(() => {
  const h = forecast.value?.historyWeeks;
  return Array.isArray(h) ? h : [];
});

const forecastRows = computed(() => {
  const f = forecast.value?.forecast;
  return Array.isArray(f) ? f : [];
});

function formatHours(v: unknown): string {
  if (v == null || v === "") return "—";
  const n = Number(v);
  if (Number.isNaN(n)) return "—";
  return n.toFixed(2);
}

function teamLabel(teamId?: number) {
  if (teamId == null) return "—";
  const t = teams.value.find((x) => x.id === teamId);
  return t?.name ?? `团队 #${teamId}`;
}

async function loadForecast() {
  const teamId = wf.teamId;
  const userId = Number(localStorage.getItem("userId"));
  if (teamId == null || !Number.isFinite(userId) || userId <= 0) {
    forecast.value = null;
    return;
  }
  forecastLoading.value = true;
  try {
    const { data } = await client.get<ForecastPayload>("/workload/teams/" + teamId + "/forecast", {
      params: { userId },
    });
    forecast.value = data;
  } catch (e: unknown) {
    forecast.value = null;
    const apiMsg = axios.isAxiosError(e) ? String((e.response?.data as { error?: string })?.error ?? "") : "";
    message.error(apiMsg || "负载预测加载失败");
  } finally {
    forecastLoading.value = false;
  }
}

const statusApplyOpen = ref(false);
const statusApplySaving = ref(false);
const statusApplyTask = ref<TaskRow | null>(null);
const statusApplyForm = reactive<{ toStatus: string; applyReason: string }>({ toStatus: "", applyReason: "" });

const statusApplyTargetOptions = computed(() => {
  const r = statusApplyTask.value;
  if (!r) return [];
  return nextTaskStatusOptions(r.status);
});

function canApplyForMine(r: TaskRow) {
  return canApplyTaskStatus(r.status) && nextTaskStatusOptions(r.status).length > 0;
}

function openStatusApply(r: TaskRow) {
  statusApplyTask.value = r;
  const opts = nextTaskStatusOptions(r.status);
  statusApplyForm.toStatus = opts[0]?.value ?? "";
  statusApplyForm.applyReason = "";
  statusApplyOpen.value = true;
}

async function submitStatusApply() {
  const r = statusApplyTask.value;
  if (!r || !statusApplyForm.toStatus) {
    message.warning("请选择目标状态");
    return Promise.reject();
  }
  statusApplySaving.value = true;
  try {
    await client.post(`/tasks/${r.id}/status-requests`, {
      toStatus: statusApplyForm.toStatus,
      applyReason: statusApplyForm.applyReason?.trim() || undefined,
    });
    message.success("已提交申请，请等待管理者或管理员审核");
    statusApplyOpen.value = false;
    statusApplyTask.value = null;
    window.dispatchEvent(new Event("messages-updated"));
    load();
  } catch (e: unknown) {
    const apiMsg = axios.isAxiosError(e) ? String((e.response?.data as { error?: string })?.error ?? "") : "";
    message.error(apiMsg || "提交失败（可能已有待审申请或流转不合法）");
    return Promise.reject();
  } finally {
    statusApplySaving.value = false;
  }
}

const columns = [
  { title: "标题", dataIndex: "title", key: "title" },
  { title: "状态", dataIndex: "status", key: "status" },
  { title: "风险", key: "risk" },
  { title: "进度", key: "progress" },
  { title: "状态申请", key: "statusReq", width: 100 },
];

async function load() {
  try {
    const { data } = await client.get<TaskRow[]>("/tasks/me");
    tasks.value = data;
  } catch {
    message.error("加载失败");
  }
}

function displayProgressPercent(r: TaskRow): number {
  const d = progressDraft.value[r.id];
  if (d !== undefined) return d;
  return r.progress ?? 0;
}

function sliderNumber(v: SliderValue): number {
  return Array.isArray(v) ? Number(v[0] ?? 0) : Number(v);
}

function onProgressSliderChange(taskId: number, v: SliderValue) {
  progressDraft.value = { ...progressDraft.value, [taskId]: sliderNumber(v) };
}

async function commitProgressSlider(r: TaskRow) {
  const p = progressDraft.value[r.id];
  if (p === undefined) return;
  await onProgress(r, p);
}

watch(
  () => wf.teamId,
  () => {
    void loadForecast();
  }
);

onMounted(() => {
  load();
  client
    .get<Team[]>("/teams/member-of")
    .then(({ data }) => {
      teams.value = data;
      if (data[0]) wf.teamId = data[0].id;
    })
    .catch(() => {});
});

async function onProgress(r: TaskRow, p: number) {
  try {
    await client.patch(`/tasks/${r.id}/progress`, { progress: p, version: r.version });
    message.success("进度已保存（已通知团队管理者）");
    await load();
  } catch (e: unknown) {
    const apiMsg = axios.isAxiosError(e) ? String((e.response?.data as { error?: string })?.error ?? "") : "";
    message.error(apiMsg || "更新失败（可能版本冲突或状态不允许）");
    await load();
  } finally {
    const next = { ...progressDraft.value };
    delete next[r.id];
    progressDraft.value = next;
  }
}

async function onLogHours() {
  try {
    await client.post(`/workload/teams/${wf.teamId}/log`, { hours: wf.hours });
    message.success("已记录");
    await loadForecast();
  } catch (e: unknown) {
    const apiMsg = axios.isAxiosError(e) ? String((e.response?.data as { error?: string })?.error ?? "") : "";
    message.error(apiMsg || "提交失败");
  }
}
</script>

<style scoped>
.cell-muted {
  color: rgba(0, 0, 0, 0.25);
  font-size: 12px;
}

.progress-percent {
  min-width: 2.75em;
  font-variant-numeric: tabular-nums;
  color: rgba(0, 0, 0, 0.65);
  font-size: 13px;
}
</style>
