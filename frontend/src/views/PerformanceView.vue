<template>
  <a-space direction="vertical" style="width: 100%" size="large">
    <a-card v-if="showCycleManagement">
      <a-form layout="inline" :model="openForm" @finish="openCycle">
        <a-form-item label="类型" name="cycleType">
          <a-select v-model:value="openForm.cycleType" style="width: 120px">
            <a-select-option value="WEEK">WEEK</a-select-option>
            <a-select-option value="MONTH">MONTH</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="区间" name="range" :rules="[{ required: true, message: '请选择' }]">
          <a-range-picker v-model:value="openForm.range" />
        </a-form-item>
        <a-button type="primary" html-type="submit">开启周期</a-button>
      </a-form>
      <a-table
        style="margin-top: 16px"
        :row-key="(r) => r.id"
        :data-source="cycles"
        :columns="cycleColumns"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'op'">
            <a-button
              v-if="!record.closedFlag"
              size="small"
              danger
              @click="openCloseModal(record.id)"
            >
              关闭并计算 FCE
            </a-button>
          </template>
          <template v-else-if="column.key === 'closed'">
            {{ record.closedFlag ? "已关闭" : "开放" }}
          </template>
        </template>
      </a-table>
    </a-card>

    <a-alert
      v-if="!showCycleManagement && cycles.length === 0"
      type="info"
      show-icon
      message="当前没有处于互评开放时间的绩效周期"
      description="互评仅在管理者已开启周期、且今天在周期开始日与结束日（含）之间时可用。"
    />

    <a-card v-if="peerEligibleCycles.length > 0" title="同事互评（周期开放时间内）">
      <a-form layout="vertical" :model="peerForm" @finish="submitPeer">
        <a-form-item label="绩效周期" name="cycleId" :rules="[{ required: true, message: '请选择周期' }]">
          <a-select
            v-model:value="peerForm.cycleId"
            placeholder="选择当前可互评的周期"
            :options="peerCycleSelectOptions"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="被评价人" name="targetUserId" :rules="[{ required: true, message: '请选择被评价人' }]">
          <a-select
            v-model:value="peerForm.targetUserId"
            allow-clear
            show-search
            placeholder="按显示名或用户名搜索"
            :options="peerTargetOptions"
            :filter-option="filterMemberOption"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="质量(1-5)">
          <a-input-number v-model:value="peerForm.quality" :min="1" :max="5" :step="0.1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="协作(1-5)">
          <a-input-number v-model:value="peerForm.collaboration" :min="1" :max="5" :step="0.1" style="width: 100%" />
        </a-form-item>
        <a-button type="primary" html-type="submit">提交互评</a-button>
      </a-form>
    </a-card>

    <a-modal
      v-model:open="closeModalOpen"
      title="关账 — 上级四维评价"
      width="720px"
      :footer="null"
      destroy-on-close
      @cancel="onCloseModalCancel"
    >
      <a-alert
        type="info"
        show-icon
        style="margin-bottom: 12px"
        message="请为每位已通过审核的成员选择评价档次（对应 FCE 四档：优秀、良好、合格、不合格）。选「默认」则该成员管理者维为四档均衡（0.25×4），与此前未录入行为一致。"
      />
      <a-table
        size="small"
        row-key="userId"
        :columns="closeModalColumns"
        :data-source="closeModalMembers"
        :pagination="false"
        :loading="closeModalLoading"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            {{ formatMemberLabel(record) }}
          </template>
          <template v-else-if="column.key === 'grade'">
            <a-select
              v-model:value="managerGrades[record.userId]"
              style="width: 100%"
              :options="managerGradeOptions"
            />
          </template>
        </template>
      </a-table>
      <a-space style="margin-top: 16px">
        <a-button @click="onCloseModalCancel">取消</a-button>
        <a-button type="primary" danger :loading="closeSubmitting" @click="submitCloseCycle">
          确认关账并计算 FCE
        </a-button>
      </a-space>
    </a-modal>
  </a-space>
</template>

<script setup lang="ts">
import type { Dayjs } from "dayjs";
import axios from "axios";
import dayjs from "dayjs";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { message } from "ant-design-vue";
import client from "../api/client";

type CycleRow = {
  id: number;
  cycleType: string;
  periodStart: string;
  periodEnd: string;
  closedFlag: number;
};

const route = useRoute();
const tid = computed(() => Number(route.params.teamId));

const role = computed(() => localStorage.getItem("role") || "MEMBER");
const showCycleManagement = computed(() => role.value === "MANAGER" || role.value === "ADMIN");

const cycles = ref<CycleRow[]>([]);
const assignableUsers = ref<{ userId: number; username: string; displayName?: string }[]>([]);

/** 关账弹窗：已批准成员 + 管理者选择的四档 one-hot（与后端 CloseCycleReq.managerRows 一致） */
const closeModalOpen = ref(false);
const closeModalLoading = ref(false);
const closeSubmitting = ref(false);
const closingCycleId = ref<number | null>(null);
const closeModalMembers = ref<{ userId: number; username: string; displayName?: string | null }[]>([]);
const managerGrades = reactive<Record<number, string>>({});

const MANAGER_GRADE_KEYS = ["EXCELLENT", "GOOD", "PASS", "FAIL", "DEFAULT"] as const;
type ManagerGradeKey = (typeof MANAGER_GRADE_KEYS)[number];

const managerGradeOptions: { label: string; value: ManagerGradeKey }[] = [
  { label: "优秀（隶属度 1,0,0,0）", value: "EXCELLENT" },
  { label: "良好（0,1,0,0）", value: "GOOD" },
  { label: "合格（0,0,1,0）", value: "PASS" },
  { label: "不合格（0,0,0,1）", value: "FAIL" },
  { label: "默认（0.25×4，不写入 managerRows）", value: "DEFAULT" },
];

const GRADE_TO_VECTOR: Record<Exclude<ManagerGradeKey, "DEFAULT">, [number, number, number, number]> = {
  EXCELLENT: [1, 0, 0, 0],
  GOOD: [0, 1, 0, 0],
  PASS: [0, 0, 1, 0],
  FAIL: [0, 0, 0, 1],
};

const closeModalColumns = [
  { title: "成员", key: "name", width: 220 },
  { title: "上级评价（四档）", key: "grade", width: 360 },
];

const myUserId = computed(() => Number(localStorage.getItem("userId")) || 0);

function isCycleOpenForPeer(c: CycleRow): boolean {
  if (c.closedFlag) return false;
  const today = dayjs().format("YYYY-MM-DD");
  const start = (c.periodStart || "").slice(0, 10);
  const end = (c.periodEnd || "").slice(0, 10);
  return today >= start && today <= end;
}

const peerEligibleCycles = computed(() => cycles.value.filter(isCycleOpenForPeer));

const peerCycleSelectOptions = computed(() =>
  peerEligibleCycles.value.map((c) => ({
    value: c.id,
    label: `#${c.id} ${c.cycleType} ${(c.periodStart || "").slice(0, 10)} ~ ${(c.periodEnd || "").slice(0, 10)}`,
  })),
);

const peerTargetOptions = computed(() =>
  assignableUsers.value
    .filter((u) => u.userId !== myUserId.value)
    .map((u) => ({
      value: u.userId,
      label: formatMemberLabel(u),
    })),
);

function formatMemberLabel(u: { username: string; displayName?: string }) {
  const name = (u.displayName && u.displayName.trim()) || u.username;
  if (name === u.username) return u.username;
  return `${name}（${u.username}）`;
}

function filterMemberOption(input: string, option: { label?: string }) {
  return (option.label ?? "").toLowerCase().includes(input.toLowerCase());
}

async function loadPeerTargets() {
  if (!tid.value) return;
  try {
    const { data } = await client.get<{ userId: number; username: string; displayName?: string }[]>(
      `/teams/${tid.value}/peer-review-targets`,
    );
    assignableUsers.value = data;
  } catch {
    assignableUsers.value = [];
  }
}

const openForm = reactive<{
  cycleType: string;
  range: [Dayjs, Dayjs] | null;
}>({
  cycleType: "MONTH",
  range: null,
});

const peerForm = reactive({
  cycleId: undefined as number | undefined,
  targetUserId: undefined as number | undefined,
  quality: 4,
  collaboration: 4,
});

const cycleColumns = [
  { title: "ID", dataIndex: "id" },
  { title: "类型", dataIndex: "cycleType" },
  { title: "开始", dataIndex: "periodStart" },
  { title: "结束", dataIndex: "periodEnd" },
  { title: "状态", key: "closed" },
  { title: "操作", key: "op" },
];

async function load() {
  if (!tid.value) return;
  try {
    const path = showCycleManagement.value
      ? `/performance/teams/${tid.value}/cycles`
      : `/performance/teams/${tid.value}/peer-review-cycles`;
    const { data } = await client.get<CycleRow[]>(path);
    cycles.value = data;
  } catch {
    cycles.value = [];
    message.error("加载周期失败");
  }
}

watch(
  peerEligibleCycles,
  (list) => {
    if (list.length === 1) peerForm.cycleId = list[0].id;
    else if (list.length === 0) peerForm.cycleId = undefined;
    else if (peerForm.cycleId != null && !list.some((c) => c.id === peerForm.cycleId)) {
      peerForm.cycleId = undefined;
    }
  },
  { immediate: true },
);

onMounted(() => {
  if (tid.value) {
    load();
    loadPeerTargets();
  }
});

watch(
  () => tid.value,
  (id) => {
    if (id) {
      load();
      loadPeerTargets();
    }
  },
);

watch(showCycleManagement, () => {
  if (tid.value) load();
});

async function openCycle() {
  if (!openForm.range?.[0] || !openForm.range[1]) {
    message.error("请选择区间");
    return Promise.reject();
  }
  try {
    await client.post(`/performance/teams/${tid.value}/cycles`, {
      cycleType: openForm.cycleType,
      periodStart: openForm.range[0].format("YYYY-MM-DD"),
      periodEnd: openForm.range[1].format("YYYY-MM-DD"),
    });
    message.success("已开启周期");
    openForm.range = null;
    load();
  } catch {
    return Promise.reject();
  }
}

function buildManagerRowsPayload(): Record<string, number[]> | undefined {
  const out: Record<string, number[]> = {};
  for (const u of closeModalMembers.value) {
    const g = managerGrades[u.userId] as ManagerGradeKey | undefined;
    if (g && g !== "DEFAULT") {
      const vec = GRADE_TO_VECTOR[g as Exclude<ManagerGradeKey, "DEFAULT">];
      if (vec) {
        out[String(u.userId)] = [...vec];
      }
    }
  }
  return Object.keys(out).length > 0 ? out : undefined;
}

async function openCloseModal(cycleId: number) {
  closingCycleId.value = cycleId;
  closeModalOpen.value = true;
  closeModalLoading.value = true;
  for (const k of Object.keys(managerGrades)) {
    delete managerGrades[Number(k)];
  }
  try {
    const { data } = await client.get<{ userId: number; username: string; displayName?: string | null }[]>(
      `/teams/${tid.value}/assignable-users`,
    );
    closeModalMembers.value = data;
    for (const u of data) {
      managerGrades[u.userId] = "DEFAULT";
    }
  } catch {
    closeModalMembers.value = [];
    message.error("加载成员列表失败（需为团队管理者或系统管理员）");
  } finally {
    closeModalLoading.value = false;
  }
}

function onCloseModalCancel() {
  closeModalOpen.value = false;
  closingCycleId.value = null;
}

async function submitCloseCycle() {
  const id = closingCycleId.value;
  if (id == null) return;
  const managerRows = buildManagerRowsPayload();
  closeSubmitting.value = true;
  try {
    await client.post(`/performance/cycles/${id}/close`, managerRows != null ? { managerRows } : {});
    message.success("已关闭并生成报告");
    closeModalOpen.value = false;
    closingCycleId.value = null;
    load();
  } catch (e) {
    const text = axios.isAxiosError(e)
      ? String((e.response?.data as { error?: string })?.error ?? e.message)
      : "关账失败";
    message.error(text);
  } finally {
    closeSubmitting.value = false;
  }
}

async function submitPeer() {
  try {
    await client.post(`/peer-reviews/teams/${tid.value}/cycles/${peerForm.cycleId}`, {
      targetUserId: peerForm.targetUserId,
      dimensions: {
        quality: peerForm.quality,
        collaboration: peerForm.collaboration,
      },
    });
    message.success("已提交");
    peerForm.targetUserId = undefined;
  } catch (e) {
    const text = axios.isAxiosError(e)
      ? String((e.response?.data as { error?: string })?.error ?? e.message)
      : "提交失败";
    message.error(text);
    return Promise.reject(e);
  }
}
</script>
