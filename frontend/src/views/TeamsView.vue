<template>
  <div v-if="!allowed"></div>
  <a-card v-else :title="isMember ? '我的团队' : '团队管理'">
    <a-space direction="vertical" style="width: 100%" size="middle">
      <a-typography-text v-if="isMember" type="secondary">
        以下为已批准加入的团队，仅可查看信息与进入团队任务；AHP、绩效配置等需团队管理者操作。
      </a-typography-text>

      <a-card v-if="isMember" type="inner" title="申请加入其他团队" size="small">
        <a-typography-text type="secondary" style="display: block; margin-bottom: 8px">
          请输入团队 ID（可向团队管理者索取）。提交后为待审核状态，需管理者在「成员」中通过。
        </a-typography-text>
        <a-space wrap align="center">
          <a-input-number v-model:value="joinTeamId" :min="1" placeholder="团队 ID" style="width: 180px" />
          <a-button type="primary" :loading="joinSaving" @click="submitJoinTeam">申请加入</a-button>
        </a-space>
      </a-card>

      <a-space wrap align="center">
        <a-typography-text>
          当前共 <strong>{{ teams.length }}</strong> 个团队
          <template v-if="isMember">（您已加入）</template>
          <template v-else-if="isAdmin">（系统全部）</template>
          <template v-else>（您作为管理者）</template>
        </a-typography-text>
        <a-button v-if="!isMember" type="primary" @click="openCreate">新建团队</a-button>
      </a-space>

      <a-table
        :row-key="(r: TeamOverview) => r.id"
        :data-source="teams"
        :columns="tableColumns"
        :loading="loading"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'actions'">
            <a-space wrap>
              <a-button type="link" size="small" @click="goTasks(record.id)">团队任务</a-button>
              <template v-if="!isMember">
                <a-button type="link" size="small" @click="goAhp(record.id)">AHP</a-button>
                <a-button type="link" size="small" @click="goPerf(record.id)">绩效</a-button>
                <a-button type="link" size="small" @click="openMembersModal(record)">成员</a-button>
                <a-button
                  v-if="record.status === 'ACTIVE'"
                  type="link"
                  size="small"
                  @click="openEditTeam(record)"
                >
                  编辑
                </a-button>
                <a-button
                  v-if="record.status === 'ACTIVE'"
                  type="link"
                  size="small"
                  danger
                  @click="confirmArchive(record)"
                >
                  归档
                </a-button>
                <a-button v-if="isAdmin" type="link" size="small" danger @click="confirmDelete(record)">
                  删除
                </a-button>
              </template>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-space>

    <a-modal
      v-model:open="createOpen"
      title="新建团队"
      :confirm-loading="createSaving"
      @ok="submitCreate"
      @cancel="createOpen = false"
    >
      <a-form layout="vertical">
        <a-form-item label="团队名称" required>
          <a-input v-model:value="createForm.name" placeholder="例如：产品研发一组" />
        </a-form-item>
        <a-form-item v-if="isAdmin" label="团队管理者" required>
          <a-select
            v-model:value="createForm.managerId"
            show-search
            placeholder="选择用户作为管理者"
            :options="managerOptions"
            :filter-option="filterUserOption"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="团队目标">
          <a-textarea v-model:value="createForm.goal" :rows="2" />
        </a-form-item>
        <a-form-item label="公告">
          <a-textarea v-model:value="createForm.announcement" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="editOpen"
      title="编辑团队"
      :confirm-loading="editSaving"
      ok-text="保存"
      cancel-text="取消"
      @ok="submitEditTeam"
      @cancel="editOpen = false"
    >
      <a-form layout="vertical">
        <a-form-item label="团队名称" required>
          <a-input v-model:value="editForm.name" placeholder="例如：产品研发一组" />
        </a-form-item>
        <a-form-item label="团队目标">
          <a-textarea v-model:value="editForm.goal" :rows="2" />
        </a-form-item>
        <a-form-item label="公告">
          <a-textarea v-model:value="editForm.announcement" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="membersModalOpen"
      :title="membersModalTitle"
      width="720px"
      :footer="null"
      destroy-on-close
      @cancel="membersModalOpen = false"
    >
      <a-spin :spinning="membersLoading">
        <a-typography-text type="secondary" style="display: block; margin-bottom: 12px">
          管理者可直接添加成员（立即通过）；待审申请可在此通过或拒绝；已通过成员可移除（不可移除团队管理者）。
        </a-typography-text>
        <a-divider orientation="left" plain>添加成员</a-divider>
        <a-space wrap style="margin-bottom: 16px">
          <a-select
            v-model:value="addMemberUserId"
            allow-clear
            show-search
            placeholder="选择要加入的用户"
            :options="inviteCandidateOptions"
            :filter-option="filterUserOption"
            style="width: 280px"
          />
          <a-button type="primary" :loading="addMemberSaving" @click="submitAddMember">加入团队</a-button>
        </a-space>
        <a-divider orientation="left" plain>成员列表</a-divider>
        <a-table
          :row-key="(r: MemberRow) => r.id"
          :data-source="membersList"
          :columns="memberColumns"
          :pagination="false"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag v-if="record.approvalStatus === 'APPROVED'" color="green">已通过</a-tag>
              <a-tag v-else-if="record.approvalStatus === 'PENDING'" color="orange">待审核</a-tag>
              <a-tag v-else-if="record.approvalStatus === 'REJECTED'" color="red">已拒绝</a-tag>
              <a-tag v-else>{{ record.approvalStatus }}</a-tag>
            </template>
            <template v-else-if="column.key === 'joined'">
              {{ formatJoined(record.joinedAt) }}
            </template>
            <template v-else-if="column.key === 'mact'">
              <a-space wrap>
                <template v-if="record.approvalStatus === 'PENDING'">
                  <a-button type="link" size="small" @click="approveMember(record)">通过</a-button>
                  <a-button type="link" size="small" danger @click="rejectMember(record)">拒绝</a-button>
                </template>
                <a-button
                  v-if="record.approvalStatus === 'APPROVED' && record.userId !== membersModalTeam?.managerId"
                  type="link"
                  size="small"
                  danger
                  @click="confirmRemoveMember(record)"
                >
                  移除
                </a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-spin>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { Modal } from "ant-design-vue";
import dayjs from "dayjs";
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { message } from "ant-design-vue";
import axios from "axios";
import client from "../api/client";

type TeamOverview = {
  id: number;
  name: string;
  goal?: string;
  announcement?: string;
  status: string;
  managerId: number;
  managerUsername: string;
  approvedMemberCount: number;
};

type UserLite = {
  id: number;
  username: string;
  displayName?: string;
  role: string;
  status?: number;
};

const router = useRouter();
const role = ref(localStorage.getItem("role") || "");
const allowed = computed(() =>
  role.value === "ADMIN" || role.value === "MANAGER" || role.value === "MEMBER"
);
const isAdmin = computed(() => role.value === "ADMIN");
const isMember = computed(() => role.value === "MEMBER");

const teams = ref<TeamOverview[]>([]);
const loading = ref(false);
const allUsers = ref<UserLite[]>([]);

const createOpen = ref(false);
const createSaving = ref(false);
const createForm = reactive({
  name: "",
  goal: "",
  announcement: "",
  managerId: undefined as number | undefined,
});

const editOpen = ref(false);
const editSaving = ref(false);
const editTeamRecord = ref<TeamOverview | null>(null);
const editForm = reactive({
  name: "",
  goal: "",
  announcement: "",
});

const joinTeamId = ref<number | null>(null);
const joinSaving = ref(false);

const managerOptions = computed(() =>
  allUsers.value
    .filter((u) => u.status !== 0)
    .map((u) => ({
      value: u.id,
      label: `${u.username}${u.displayName ? `（${u.displayName}）` : ""} · ${u.role}`,
    }))
);

const tableColumns = computed(() => [
  { title: "ID", dataIndex: "id", width: 70 },
  { title: "名称", dataIndex: "name" },
  { title: "管理者", dataIndex: "managerUsername", width: 130 },
  { title: "成员数", dataIndex: "approvedMemberCount", width: 90 },
  { title: "状态", dataIndex: "status", width: 100 },
  { title: "快捷入口", key: "actions", width: isMember.value ? 140 : 380 },
]);

type MemberRow = {
  id: number;
  teamId: number;
  userId: number;
  approvalStatus: string;
  username: string;
  displayName?: string;
  joinedAt?: string;
};

type InviteCandidate = { userId: number; username: string; displayName?: string };

const membersModalOpen = ref(false);
const membersModalTeam = ref<TeamOverview | null>(null);
const membersModalTitle = computed(() =>
  membersModalTeam.value ? `成员管理 — ${membersModalTeam.value.name}` : "成员管理"
);
const membersLoading = ref(false);
const membersList = ref<MemberRow[]>([]);
const inviteCandidates = ref<InviteCandidate[]>([]);
const addMemberUserId = ref<number | undefined>(undefined);
const addMemberSaving = ref(false);

const inviteCandidateOptions = computed(() =>
  inviteCandidates.value.map((u) => ({
    value: u.userId,
    label: u.displayName?.trim()
      ? `${u.displayName.trim()}（${u.username}）`
      : u.username,
  }))
);

const memberColumns = [
  { title: "用户名", dataIndex: "username", key: "username", width: 120 },
  { title: "显示名", dataIndex: "displayName", key: "displayName", ellipsis: true },
  { title: "状态", key: "status", width: 100 },
  { title: "加入时间", key: "joined", width: 160 },
  { title: "操作", key: "mact", width: 200 },
];

function formatJoined(s?: string) {
  if (!s) return "—";
  return dayjs(s).format("YYYY-MM-DD HH:mm");
}

async function refreshMembersModalData() {
  const t = membersModalTeam.value;
  if (!t) return;
  membersLoading.value = true;
  try {
    const [mRes, cRes] = await Promise.all([
      client.get<MemberRow[]>(`/teams/${t.id}/members`),
      client.get<InviteCandidate[]>(`/teams/${t.id}/invite-candidates`),
    ]);
    membersList.value = mRes.data;
    inviteCandidates.value = cRes.data;
  } catch {
    message.error("加载成员失败");
    membersList.value = [];
    inviteCandidates.value = [];
  } finally {
    membersLoading.value = false;
  }
}

function openMembersModal(record: TeamOverview) {
  membersModalTeam.value = record;
  addMemberUserId.value = undefined;
  membersModalOpen.value = true;
  refreshMembersModalData();
}

async function submitAddMember() {
  const t = membersModalTeam.value;
  if (!t || addMemberUserId.value == null) {
    message.warning("请选择要添加的用户");
    return;
  }
  addMemberSaving.value = true;
  try {
    await client.post(`/teams/${t.id}/members`, { userId: addMemberUserId.value });
    message.success("已加入团队");
    addMemberUserId.value = undefined;
    await refreshMembersModalData();
    load();
  } catch {
    message.error("添加失败");
  } finally {
    addMemberSaving.value = false;
  }
}

async function approveMember(row: MemberRow) {
  const t = membersModalTeam.value;
  if (!t) return;
  try {
    await client.post(`/teams/${t.id}/members/${row.id}/approve`);
    message.success("已通过");
    await refreshMembersModalData();
    load();
  } catch {
    message.error("操作失败");
  }
}

async function rejectMember(row: MemberRow) {
  const t = membersModalTeam.value;
  if (!t) return;
  try {
    await client.post(`/teams/${t.id}/members/${row.id}/reject`);
    message.success("已拒绝");
    await refreshMembersModalData();
    load();
  } catch {
    message.error("操作失败");
  }
}

function confirmRemoveMember(row: MemberRow) {
  const t = membersModalTeam.value;
  if (!t) return;
  Modal.confirm({
    title: `将「${row.displayName || row.username}」移出团队？`,
    content: "移除后该用户不再参与本团队任务与绩效，可稍后重新添加。",
    okText: "移除",
    okType: "danger",
    onOk: async () => {
      try {
        await client.delete(`/teams/${t.id}/members/${row.id}`);
        message.success("已移除");
        await refreshMembersModalData();
        load();
      } catch {
        message.error("移除失败");
        return Promise.reject();
      }
    },
  });
}

function filterUserOption(input: string, option: { label?: string }) {
  return (option.label ?? "").toLowerCase().includes(input.toLowerCase());
}

async function load() {
  loading.value = true;
  try {
    const url = isMember.value ? "/teams/member-of/overview" : "/teams/overview";
    const { data } = await client.get<TeamOverview[]>(url);
    teams.value = data;
  } catch {
    message.error("加载团队列表失败");
  } finally {
    loading.value = false;
  }
}

async function loadUsersForAdmin() {
  if (!isAdmin.value) return;
  try {
    const { data } = await client.get<UserLite[]>("/admin/users");
    allUsers.value = data;
  } catch {
    message.error("加载用户列表失败");
  }
}

function openCreate() {
  createForm.name = "";
  createForm.goal = "";
  createForm.announcement = "";
  createForm.managerId = undefined;
  if (isAdmin.value) loadUsersForAdmin();
  createOpen.value = true;
}

function openEditTeam(record: TeamOverview) {
  editTeamRecord.value = record;
  editForm.name = record.name ?? "";
  editForm.goal = record.goal ?? "";
  editForm.announcement = record.announcement ?? "";
  editOpen.value = true;
}

async function submitEditTeam() {
  const rec = editTeamRecord.value;
  const name = editForm.name?.trim();
  if (!rec || !name) {
    message.warning("请填写团队名称");
    return Promise.reject();
  }
  editSaving.value = true;
  try {
    await client.put(`/teams/${rec.id}`, {
      name,
      goal: editForm.goal?.trim() || null,
      announcement: editForm.announcement?.trim() || null,
    });
    message.success("已保存");
    editOpen.value = false;
    editTeamRecord.value = null;
    await load();
  } catch (e: unknown) {
    const apiMsg = axios.isAxiosError(e) ? String((e.response?.data as { error?: string })?.error ?? "") : "";
    message.error(apiMsg || "保存失败（非本团队管理者无法编辑）");
    return Promise.reject();
  } finally {
    editSaving.value = false;
  }
}

async function submitJoinTeam() {
  const id = joinTeamId.value;
  if (id == null || id < 1 || !Number.isFinite(id)) {
    message.warning("请输入有效的团队 ID");
    return;
  }
  joinSaving.value = true;
  try {
    await client.post(`/teams/${id}/join`);
    message.success("申请已提交，请等待管理者审核");
    joinTeamId.value = null;
    await load();
  } catch (e: unknown) {
    const apiMsg = axios.isAxiosError(e) ? String((e.response?.data as { error?: string })?.error ?? "") : "";
    message.error(apiMsg || "申请失败");
  } finally {
    joinSaving.value = false;
  }
}

async function submitCreate() {
  const name = createForm.name?.trim();
  if (!name) {
    message.warning("请填写团队名称");
    return Promise.reject();
  }
  if (isAdmin.value && createForm.managerId == null) {
    message.warning("请选择团队管理者");
    return Promise.reject();
  }
  createSaving.value = true;
  try {
    const body: Record<string, unknown> = {
      name,
      goal: createForm.goal?.trim() || undefined,
      announcement: createForm.announcement?.trim() || undefined,
    };
    if (isAdmin.value) body.managerId = createForm.managerId;
    await client.post("/teams", body);
    message.success("团队已创建");
    createOpen.value = false;
    load();
  } catch {
    message.error("创建失败");
    return Promise.reject();
  } finally {
    createSaving.value = false;
  }
}

function goTasks(teamId: number) {
  router.push(`/tasks/${teamId}`);
}

function goAhp(teamId: number) {
  router.push(`/ahp/${teamId}`);
}

function goPerf(teamId: number) {
  router.push(`/performance/${teamId}`);
}

function confirmArchive(record: TeamOverview) {
  Modal.confirm({
    title: `归档团队「${record.name}」？`,
    content: "归档后可在数据库中保留记录，部分功能可能受限。",
    okText: "归档",
    onOk: async () => {
      await client.post(`/teams/${record.id}/archive`);
      message.success("已归档");
      load();
    },
  });
}

function confirmDelete(record: TeamOverview) {
  Modal.confirm({
    title: `删除团队「${record.name}」？`,
    content: "将级联删除任务等关联数据，不可恢复。",
    okText: "删除",
    okType: "danger",
    onOk: async () => {
      await client.delete(`/admin/teams/${record.id}`);
      message.success("已删除");
      load();
    },
  });
}

onMounted(() => {
  if (!allowed.value) {
    router.replace("/");
    return;
  }
  load();
});
</script>
