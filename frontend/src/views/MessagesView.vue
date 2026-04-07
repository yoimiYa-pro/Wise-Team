<template>
  <a-space direction="vertical" style="width: 100%" size="large">
    <a-card title="消息中心">
      <a-space style="margin-bottom: 12px">
        <a-button type="primary" ghost @click="loadMessages">刷新</a-button>
        <a-button @click="onReadAll">全部标为已读</a-button>
      </a-space>
      <a-list :data-source="messages" :loading="msgLoading" bordered>
        <template #renderItem="{ item }">
          <a-list-item
            :class="{ unread: item.readFlag === 0 }"
            @click="onClickMessage(item)"
            style="cursor: pointer"
          >
            <a-list-item-meta :title="item.title" :description="item.body || '（无正文）'" />
            <template #actions>
              <span class="muted">{{ formatTime(item.createdAt) }}</span>
              <a-tag v-if="item.readFlag === 0" color="blue">未读</a-tag>
            </template>
          </a-list-item>
        </template>
        <template #empty><a-empty description="暂无消息" /></template>
      </a-list>
    </a-card>

    <a-card v-if="isAdmin" title="待审批 — 个人信息变更">
      <a-table
        :row-key="(r: ProfilePendingRow) => r.request.id"
        :data-source="profilePending"
        :columns="profileColumns"
        :loading="profilePendLoading"
        :pagination="false"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'skills'">
            <span class="ellipsis-cell">{{ record.request.proposedSkillsJson }}</span>
          </template>
          <template v-else-if="column.key === 'reason'">
            {{ record.request.applyReason || "—" }}
          </template>
          <template v-else-if="column.key === 'act'">
            <a-space>
              <a-button type="link" size="small" @click="openProfileDetail(record)">详情</a-button>
              <a-button type="primary" size="small" @click="openProfileApprove(record)">通过</a-button>
              <a-button danger size="small" @click="openProfileReject(record)">驳回</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
      <a-empty v-if="!profilePendLoading && !profilePending.length" description="暂无待审批" />
    </a-card>

    <a-card v-if="canApprove" title="待审批 — 任务状态变更">
      <a-table
        :row-key="(r: PendingRow) => r.request.id"
        :data-source="pending"
        :columns="pendingColumns"
        :loading="pendLoading"
        :pagination="false"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'flow'">
            {{ record.request.fromStatus }} → {{ record.request.toStatus }}
          </template>
          <template v-else-if="column.key === 'reason'">
            {{ record.request.applyReason || "—" }}
          </template>
          <template v-else-if="column.key === 'act'">
            <a-space>
              <a-button type="primary" size="small" @click="openApprove(record)">通过</a-button>
              <a-button danger size="small" @click="openReject(record)">驳回</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
      <a-empty v-if="!pendLoading && !pending.length" description="暂无待审批" />
    </a-card>

    <a-modal v-model:open="approveOpen" title="通过申请" @ok="submitApprove">
      <a-form-item label="审批备注（可选）">
        <a-textarea v-model:value="approveComment" :rows="2" />
      </a-form-item>
    </a-modal>

    <a-modal v-model:open="rejectOpen" title="驳回申请" @ok="submitReject">
      <a-form-item label="驳回原因（必填）" required>
        <a-textarea v-model:value="rejectComment" :rows="3" placeholder="请说明驳回原因与整改要求" />
      </a-form-item>
    </a-modal>

    <a-modal v-model:open="profileApproveOpen" title="通过个人信息变更" @ok="submitProfileApprove">
      <a-form-item label="审批备注（可选）">
        <a-textarea v-model:value="profileApproveComment" :rows="2" />
      </a-form-item>
    </a-modal>

    <a-modal v-model:open="profileRejectOpen" title="驳回个人信息变更" @ok="submitProfileReject">
      <a-form-item label="驳回原因（必填）" required>
        <a-textarea v-model:value="profileRejectComment" :rows="3" placeholder="请说明驳回原因" />
      </a-form-item>
    </a-modal>

    <a-modal
      v-model:open="profileDetailOpen"
      title="个人信息变更详情"
      width="760"
      :footer="null"
      destroy-on-close
    >
      <template v-if="activeProfileDetail">
        <a-descriptions bordered size="small" :column="1" style="margin-bottom: 16px">
          <a-descriptions-item label="申请人">{{ activeProfileDetail.applicantUsername }}</a-descriptions-item>
          <a-descriptions-item label="申请时间">{{
            formatTime(activeProfileDetail.request.createdAt)
          }}</a-descriptions-item>
          <a-descriptions-item label="申请说明">{{
            activeProfileDetail.request.applyReason?.trim() || "—"
          }}</a-descriptions-item>
        </a-descriptions>
        <a-row :gutter="16">
          <a-col :xs="24" :md="12">
            <a-typography-title :level="5">变更前（当前已生效）</a-typography-title>
            <a-descriptions bordered size="small" :column="1">
              <a-descriptions-item label="显示名">{{
                activeProfileDetail.currentDisplayName?.trim() || "—"
              }}</a-descriptions-item>
              <a-descriptions-item label="额定周工时">{{
                activeProfileDetail.currentBaseCapacity ?? "—"
              }}</a-descriptions-item>
              <a-descriptions-item label="技能 JSON">
                <pre class="detail-json-block">{{ prettySkillsJson(activeProfileDetail.currentSkillsJson) }}</pre>
              </a-descriptions-item>
            </a-descriptions>
          </a-col>
          <a-col :xs="24" :md="12">
            <a-typography-title :level="5">变更后（申请内容）</a-typography-title>
            <a-descriptions bordered size="small" :column="1">
              <a-descriptions-item label="显示名">{{
                activeProfileDetail.request.proposedDisplayName || "—"
              }}</a-descriptions-item>
              <a-descriptions-item label="额定周工时">{{
                activeProfileDetail.request.proposedBaseCapacity
              }}</a-descriptions-item>
              <a-descriptions-item label="技能 JSON">
                <pre class="detail-json-block">{{
                  prettySkillsJson(activeProfileDetail.request.proposedSkillsJson)
                }}</pre>
              </a-descriptions-item>
            </a-descriptions>
          </a-col>
        </a-row>
        <div style="margin-top: 16px; text-align: right">
          <a-button type="primary" @click="profileDetailOpen = false">关闭</a-button>
        </div>
      </template>
    </a-modal>
  </a-space>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { message } from "ant-design-vue";
import dayjs from "dayjs";
import client from "../api/client";

type Msg = {
  id: number;
  title: string;
  body?: string;
  readFlag: number;
  createdAt?: string;
};

type PendingRow = {
  request: {
    id: number;
    taskId: number;
    fromStatus: string;
    toStatus: string;
    applyReason?: string;
  };
  taskTitle: string;
  applicantUsername: string;
  teamId: number;
  teamName: string;
};

type ProfilePendingRow = {
  request: {
    id: number;
    userId: number;
    proposedDisplayName: string;
    proposedSkillsJson: string;
    proposedBaseCapacity: number;
    applyReason?: string;
    createdAt?: string;
  };
  applicantUsername: string;
  currentDisplayName?: string | null;
  currentBaseCapacity?: number | null;
  currentSkillsJson?: string | null;
};

const role = ref(localStorage.getItem("role") || "");
const isAdmin = computed(() => role.value === "ADMIN");
const canApprove = computed(() => role.value === "ADMIN" || role.value === "MANAGER");

const messages = ref<Msg[]>([]);
const msgLoading = ref(false);
const pending = ref<PendingRow[]>([]);
const pendLoading = ref(false);

const profilePending = ref<ProfilePendingRow[]>([]);
const profilePendLoading = ref(false);

const pendingColumns = [
  { title: "团队", dataIndex: "teamName", key: "teamName", width: 120 },
  { title: "任务", dataIndex: "taskTitle", key: "taskTitle" },
  { title: "申请人", dataIndex: "applicantUsername", key: "applicantUsername", width: 100 },
  { title: "流转", key: "flow", width: 180 },
  { title: "说明", key: "reason", ellipsis: true },
  { title: "操作", key: "act", width: 160 },
];

const profileColumns = [
  { title: "申请人", dataIndex: "applicantUsername", key: "applicantUsername", width: 120 },
  { title: "拟显示名", dataIndex: ["request", "proposedDisplayName"], key: "dn", width: 140 },
  { title: "拟额定工时", dataIndex: ["request", "proposedBaseCapacity"], key: "cap", width: 100 },
  { title: "拟技能 JSON", key: "skills", ellipsis: true },
  { title: "说明", key: "reason", ellipsis: true },
  { title: "操作", key: "act", width: 240 },
];

const approveOpen = ref(false);
const rejectOpen = ref(false);
const activePending = ref<PendingRow | null>(null);
const approveComment = ref("");
const rejectComment = ref("");

const profileApproveOpen = ref(false);
const profileRejectOpen = ref(false);
const activeProfilePending = ref<ProfilePendingRow | null>(null);
const profileApproveComment = ref("");
const profileRejectComment = ref("");

const profileDetailOpen = ref(false);
const activeProfileDetail = ref<ProfilePendingRow | null>(null);

async function loadMessages() {
  msgLoading.value = true;
  try {
    const { data } = await client.get<Msg[]>("/messages");
    messages.value = data;
  } catch {
    message.error("加载消息失败");
  } finally {
    msgLoading.value = false;
  }
}

async function loadPending() {
  if (!canApprove.value) return;
  pendLoading.value = true;
  try {
    const { data } = await client.get<PendingRow[]>("/task-status-requests/pending");
    pending.value = data;
  } catch {
    pending.value = [];
  } finally {
    pendLoading.value = false;
  }
}

async function loadProfilePending() {
  if (!isAdmin.value) return;
  profilePendLoading.value = true;
  try {
    const { data } = await client.get<ProfilePendingRow[]>("/profile-requests/pending");
    profilePending.value = data;
  } catch {
    profilePending.value = [];
  } finally {
    profilePendLoading.value = false;
  }
}

function formatTime(s?: string) {
  if (!s) return "";
  return dayjs(s).format("YYYY-MM-DD HH:mm");
}

function prettySkillsJson(s?: string | null) {
  if (s == null || !String(s).trim()) return "—";
  try {
    return JSON.stringify(JSON.parse(String(s)), null, 2);
  } catch {
    return String(s);
  }
}

function openProfileDetail(r: ProfilePendingRow) {
  activeProfileDetail.value = r;
  profileDetailOpen.value = true;
}

async function onClickMessage(item: Msg) {
  if (item.readFlag === 0) {
    try {
      await client.patch(`/messages/${item.id}/read`);
      item.readFlag = 1;
      window.dispatchEvent(new Event("messages-updated"));
    } catch {
      /* ignore */
    }
  }
}

async function onReadAll() {
  try {
    await client.post("/messages/read-all");
    messages.value.forEach((m) => (m.readFlag = 1));
    window.dispatchEvent(new Event("messages-updated"));
    message.success("已全部标为已读");
  } catch {
    message.error("操作失败");
  }
}

function openApprove(r: PendingRow) {
  activePending.value = r;
  approveComment.value = "";
  approveOpen.value = true;
}

function openReject(r: PendingRow) {
  activePending.value = r;
  rejectComment.value = "";
  rejectOpen.value = true;
}

async function submitApprove() {
  const r = activePending.value;
  if (!r) return;
  try {
    await client.post(`/task-status-requests/${r.request.id}/approve`, {
      reviewComment: approveComment.value?.trim() || undefined,
    });
    message.success("已通过");
    approveOpen.value = false;
    loadPending();
    loadMessages();
    window.dispatchEvent(new Event("messages-updated"));
  } catch {
    message.error("操作失败");
    return Promise.reject();
  }
}

async function submitReject() {
  const r = activePending.value;
  if (!r) return;
  const c = rejectComment.value?.trim() ?? "";
  if (!c) {
    message.warning("请填写驳回原因");
    return Promise.reject();
  }
  try {
    await client.post(`/task-status-requests/${r.request.id}/reject`, { reviewComment: c });
    message.success("已驳回");
    rejectOpen.value = false;
    loadPending();
    loadMessages();
    window.dispatchEvent(new Event("messages-updated"));
  } catch {
    message.error("操作失败");
    return Promise.reject();
  }
}

function openProfileApprove(r: ProfilePendingRow) {
  activeProfilePending.value = r;
  profileApproveComment.value = "";
  profileApproveOpen.value = true;
}

function openProfileReject(r: ProfilePendingRow) {
  activeProfilePending.value = r;
  profileRejectComment.value = "";
  profileRejectOpen.value = true;
}

async function submitProfileApprove() {
  const r = activeProfilePending.value;
  if (!r) return;
  try {
    await client.post(`/profile-requests/${r.request.id}/approve`, {
      reviewComment: profileApproveComment.value?.trim() || undefined,
    });
    message.success("已通过");
    profileApproveOpen.value = false;
    loadProfilePending();
    loadMessages();
    window.dispatchEvent(new Event("messages-updated"));
  } catch {
    message.error("操作失败");
    return Promise.reject();
  }
}

async function submitProfileReject() {
  const r = activeProfilePending.value;
  if (!r) return;
  const c = profileRejectComment.value?.trim() ?? "";
  if (!c) {
    message.warning("请填写驳回原因");
    return Promise.reject();
  }
  try {
    await client.post(`/profile-requests/${r.request.id}/reject`, { reviewComment: c });
    message.success("已驳回");
    profileRejectOpen.value = false;
    loadProfilePending();
    loadMessages();
    window.dispatchEvent(new Event("messages-updated"));
  } catch {
    message.error("操作失败");
    return Promise.reject();
  }
}

onMounted(() => {
  loadMessages();
  loadPending();
  loadProfilePending();
});
</script>

<style scoped>
.unread {
  background: #f0f7ff;
}
.muted {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.ellipsis-cell {
  display: inline-block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.detail-json-block {
  margin: 0;
  max-height: 240px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
  background: rgba(0, 0, 0, 0.04);
  padding: 8px;
  border-radius: 4px;
}
</style>
