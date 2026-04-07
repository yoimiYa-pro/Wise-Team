<template>
  <a-space direction="vertical" style="width: 100%" size="large">
    <a-card :title="`团队 ${tid} — 绩效周期`">
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
        :row-key="(r: CycleRow) => r.id"
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
              @click="closeCycle(record.id)"
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
    <a-card title="同事互评（开放周期内）">
      <a-form layout="vertical" :model="peerForm" @finish="submitPeer">
        <a-form-item label="周期 ID" name="cycleId" :rules="[{ required: true }]">
          <a-input-number v-model:value="peerForm.cycleId" style="width: 100%" />
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
  </a-space>
</template>

<script setup lang="ts">
import type { Dayjs } from "dayjs";
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

const cycles = ref<CycleRow[]>([]);
const assignableUsers = ref<{ userId: number; username: string; displayName?: string }[]>([]);

const myUserId = computed(() => Number(localStorage.getItem("userId")) || 0);

const peerTargetOptions = computed(() =>
  assignableUsers.value
    .filter((u) => u.userId !== myUserId.value)
    .map((u) => ({
      value: u.userId,
      label: formatMemberLabel(u),
    }))
);

function formatMemberLabel(u: { username: string; displayName?: string }) {
  const name = (u.displayName && u.displayName.trim()) || u.username;
  if (name === u.username) return u.username;
  return `${name}（${u.username}）`;
}

function filterMemberOption(input: string, option: { label?: string }) {
  return (option.label ?? "").toLowerCase().includes(input.toLowerCase());
}

async function loadAssignableUsers() {
  if (!tid.value) return;
  try {
    const { data } = await client.get<{ userId: number; username: string; displayName?: string }[]>(
      `/teams/${tid.value}/assignable-users`
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
  const { data } = await client.get<CycleRow[]>(`/performance/teams/${tid.value}/cycles`);
  cycles.value = data;
}

onMounted(() => {
  if (tid.value) {
    load().catch(() => message.error("加载周期失败"));
    loadAssignableUsers();
  }
});

watch(
  () => tid.value,
  (id) => {
    if (id) {
      load().catch(() => message.error("加载周期失败"));
      loadAssignableUsers();
    }
  }
);

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

async function closeCycle(id: number) {
  await client.post(`/performance/cycles/${id}/close`, {});
  message.success("已关闭并生成报告");
  load();
}

async function submitPeer() {
  await client.post(`/peer-reviews/teams/${tid.value}/cycles/${peerForm.cycleId}`, {
    targetUserId: peerForm.targetUserId,
    dimensions: {
      quality: peerForm.quality,
      collaboration: peerForm.collaboration,
    },
  });
  message.success("已提交");
  peerForm.cycleId = undefined;
  peerForm.targetUserId = undefined;
}
</script>
