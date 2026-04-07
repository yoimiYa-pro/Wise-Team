<template>
  <div v-if="role !== 'ADMIN'"></div>
  <a-card v-else title="用户管理">
    <a-button type="primary" @click="openCreate">新建用户</a-button>
    <a-table
      style="margin-top: 16px"
      :row-key="(r: UserRow) => r.id"
      :data-source="users"
      :columns="columns"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'default'">
            {{ record.status === 1 ? "启用" : "已禁用" }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
            <a-button
              v-if="record.status === 1 && !isSelf(record.id)"
              type="link"
              size="small"
              danger
              @click="confirmDisable(record)"
            >
              禁用
            </a-button>
            <a-button v-else-if="record.status !== 1" type="link" size="small" @click="confirmEnable(record)">
              恢复
            </a-button>
            <span v-if="record.status === 1 && isSelf(record.id)" class="muted">当前账号</span>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="open" title="新建用户" width="640" @ok="submitCreate" @cancel="open = false">
      <a-form ref="formRef" :model="createForm" layout="vertical">
        <a-form-item name="username" label="用户名" :rules="[{ required: true }]">
          <a-input v-model:value="createForm.username" />
        </a-form-item>
        <a-form-item name="password" label="密码" :rules="[{ required: true }]">
          <a-input-password v-model:value="createForm.password" />
        </a-form-item>
        <a-form-item name="displayName" label="显示名">
          <a-input v-model:value="createForm.displayName" />
        </a-form-item>
        <a-form-item name="role" label="角色" :rules="[{ required: true }]">
          <a-input v-model:value="createForm.role" placeholder="ADMIN / MANAGER / MEMBER" />
        </a-form-item>
        <a-form-item label="技能与熟练度">
          <SkillPickerRows v-model:rows="skillRows" :catalog="catalog" />
        </a-form-item>
        <a-space>
          <a-form-item label="额定工时"><a-input-number v-model:value="createForm.baseCapacity" /></a-form-item>
          <a-form-item label="绩效基线"><a-input-number v-model:value="createForm.avgPerformance" /></a-form-item>
        </a-space>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="editOpen"
      :title="`编辑用户：${editingUsername}`"
      width="640"
      :confirm-loading="editSaving"
      @ok="submitEdit"
      @cancel="editOpen = false"
    >
      <a-form ref="editFormRef" :model="editForm" layout="vertical">
        <a-form-item label="用户名">
          <a-input :value="editingUsername" disabled />
        </a-form-item>
        <a-form-item name="displayName" label="显示名">
          <a-input v-model:value="editForm.displayName" />
        </a-form-item>
        <a-form-item name="role" label="角色" :rules="[{ required: true, message: '请填写角色' }]">
          <a-input v-model:value="editForm.role" placeholder="ADMIN / MANAGER / MEMBER" />
        </a-form-item>
        <a-form-item label="账号状态">
          <a-radio-group v-model:value="editForm.status">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0" :disabled="isSelf(editingId)">禁用（无法登录）</a-radio>
          </a-radio-group>
          <div v-if="isSelf(editingId)" class="muted" style="margin-top: 4px">不能禁用自己的账号</div>
        </a-form-item>
        <a-form-item label="技能与熟练度">
          <SkillPickerRows v-model:rows="editSkillRows" :catalog="catalog" />
        </a-form-item>
        <a-space wrap>
          <a-form-item label="额定工时"><a-input-number v-model:value="editForm.baseCapacity" /></a-form-item>
          <a-form-item label="绩效基线"><a-input-number v-model:value="editForm.avgPerformance" /></a-form-item>
          <a-form-item label="延期历史分">
            <a-input-number v-model:value="editForm.delayHistoryScore" :step="0.01" style="min-width: 120px" />
          </a-form-item>
        </a-space>
        <a-form-item label="新密码（可选）">
          <a-input-password v-model:value="editPassword" placeholder="留空则不修改；至少 4 位" autocomplete="new-password" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { Modal } from "ant-design-vue";
import { onMounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import type { FormInstance } from "ant-design-vue";
import { message } from "ant-design-vue";
import client from "../api/client";
import SkillPickerRows from "../components/SkillPickerRows.vue";
import {
  buildSkillsJson,
  parseSkillsJson,
  type SkillCatalogEntry,
  type SkillRow,
} from "../constants/skillTiers";

type UserRow = {
  id: number;
  username: string;
  displayName?: string;
  role: string;
  avgPerformance?: number;
  status?: number;
};

type UserDetail = {
  id: number;
  username: string;
  displayName?: string;
  role: string;
  skillsJson?: string;
  baseCapacity?: number;
  avgPerformance?: number;
  delayHistoryScore?: number;
  status?: number;
};

const router = useRouter();
const role = ref(localStorage.getItem("role") || "");
const currentUserId = ref(Number(localStorage.getItem("userId")) || 0);

const users = ref<UserRow[]>([]);
const open = ref(false);
const formRef = ref<FormInstance>();
const catalog = ref<SkillCatalogEntry[]>([]);
const skillRows = ref<SkillRow[]>([]);

const editOpen = ref(false);
const editFormRef = ref<FormInstance>();
const editingId = ref(0);
const editingUsername = ref("");
const editSkillRows = ref<SkillRow[]>([]);
const editPassword = ref("");
const editSaving = ref(false);

const createForm = reactive({
  username: "",
  password: "",
  displayName: "",
  role: "MEMBER",
  baseCapacity: 40,
  avgPerformance: 75,
});

const editForm = reactive({
  displayName: "",
  role: "",
  baseCapacity: 40 as number | undefined,
  avgPerformance: 75 as number | undefined,
  delayHistoryScore: 0 as number | undefined,
  status: 1,
});

const columns = [
  { title: "ID", dataIndex: "id", width: 70 },
  { title: "用户名", dataIndex: "username" },
  { title: "显示名", dataIndex: "displayName" },
  { title: "角色", dataIndex: "role", width: 110 },
  { title: "绩效均分", dataIndex: "avgPerformance", width: 100 },
  { title: "状态", key: "status", width: 100 },
  { title: "操作", key: "action", width: 220 },
];

function isSelf(id: number) {
  return currentUserId.value === id;
}

async function loadCatalog() {
  try {
    const { data } = await client.get<{ skillCode: string; label: string }[]>("/skill-options/catalog");
    catalog.value = data.map((x) => ({ skillCode: x.skillCode, label: x.label }));
  } catch {
    message.error("加载技能目录失败");
  }
}

function openCreate() {
  open.value = true;
}

watch(open, (v) => {
  if (v) {
    loadCatalog();
    skillRows.value = [];
  }
});

watch(editOpen, (v) => {
  if (v) loadCatalog();
});

async function load() {
  const { data } = await client.get<UserRow[]>("/admin/users");
  users.value = data;
}

onMounted(() => {
  if (role.value !== "ADMIN") {
    router.replace("/");
    return;
  }
  load().catch(() => message.error("加载失败"));
});

async function openEdit(record: UserRow) {
  try {
    const { data } = await client.get<UserDetail>(`/admin/users/${record.id}`);
    editingId.value = data.id;
    editingUsername.value = data.username;
    editForm.displayName = data.displayName ?? "";
    editForm.role = data.role;
    editForm.baseCapacity = data.baseCapacity != null ? Number(data.baseCapacity) : 40;
    editForm.avgPerformance = data.avgPerformance != null ? Number(data.avgPerformance) : 75;
    editForm.delayHistoryScore =
      data.delayHistoryScore != null ? Number(data.delayHistoryScore) : 0;
    editForm.status = data.status === 0 ? 0 : 1;
    editSkillRows.value = parseSkillsJson(data.skillsJson);
    editPassword.value = "";
    editOpen.value = true;
  } catch {
    message.error("加载用户详情失败");
  }
}

function confirmDisable(record: UserRow) {
  Modal.confirm({
    title: `禁用用户「${record.username}」？`,
    content: "禁用后该用户无法登录，相关数据仍保留。可稍后在编辑或此处「恢复」重新启用。",
    okText: "禁用",
    okType: "danger",
    onOk: () => doDisable(record.id),
  });
}

function confirmEnable(record: UserRow) {
  Modal.confirm({
    title: `恢复用户「${record.username}」？`,
    content: "恢复后该用户可正常登录。",
    onOk: () => doEnable(record.id),
  });
}

async function doDisable(id: number) {
  try {
    await client.post(`/admin/users/${id}/disable`);
    message.success("已禁用");
    load();
  } catch {
    message.error("操作失败");
  }
}

async function doEnable(id: number) {
  try {
    await client.post(`/admin/users/${id}/enable`);
    message.success("已恢复");
    load();
  } catch {
    message.error("操作失败");
  }
}

async function submitCreate() {
  try {
    await formRef.value?.validate();
    const codes = skillRows.value.map((r) => r.skillCode?.trim()).filter(Boolean) as string[];
    const uniq = new Set(codes);
    if (uniq.size !== codes.length) {
      message.warning("请勿重复选择同一技能");
      return Promise.reject();
    }
    await client.post("/admin/users", {
      username: createForm.username,
      password: createForm.password,
      displayName: createForm.displayName,
      role: createForm.role,
      skillsJson: buildSkillsJson(skillRows.value),
      baseCapacity: createForm.baseCapacity ?? 40,
      avgPerformance: createForm.avgPerformance ?? 75,
    });
    message.success("已创建");
    open.value = false;
    formRef.value?.resetFields();
    skillRows.value = [];
    load();
  } catch {
    return Promise.reject();
  }
}

async function submitEdit() {
  const codes = editSkillRows.value.map((r) => r.skillCode?.trim()).filter(Boolean) as string[];
  const uniq = new Set(codes);
  if (uniq.size !== codes.length) {
    message.warning("请勿重复选择同一技能");
    return Promise.reject();
  }
  if (editForm.status === 0 && isSelf(editingId.value)) {
    message.warning("不能禁用自己的账号");
    return Promise.reject();
  }
  const pwd = editPassword.value?.trim() ?? "";
  if (pwd.length > 0 && pwd.length < 4) {
    message.warning("新密码至少 4 位，或留空不修改");
    return Promise.reject();
  }
  editSaving.value = true;
  try {
    await editFormRef.value?.validate();
    await client.put(`/admin/users/${editingId.value}`, {
      displayName: editForm.displayName,
      role: editForm.role,
      skillsJson: buildSkillsJson(editSkillRows.value),
      baseCapacity: editForm.baseCapacity ?? 40,
      avgPerformance: editForm.avgPerformance ?? 75,
      delayHistoryScore: editForm.delayHistoryScore ?? 0,
      status: editForm.status,
    });
    if (pwd.length >= 4) {
      await client.post(`/admin/users/${editingId.value}/password`, { password: pwd });
    }
    message.success("已保存");
    editOpen.value = false;
    load();
  } catch {
    message.error("保存失败");
    return Promise.reject();
  } finally {
    editSaving.value = false;
  }
}
</script>

<style scoped>
.muted {
  color: rgba(0, 0, 0, 0.35);
  font-size: 12px;
}
</style>
