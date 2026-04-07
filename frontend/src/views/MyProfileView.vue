<template>
  <a-space direction="vertical" style="width: 100%" size="large">
    <a-card title="个人信息">
      <a-spin :spinning="loading">
        <a-alert
          v-if="pendingRequest"
          type="warning"
          show-icon
          style="margin-bottom: 16px"
          message="您有一条待管理员审核的修改申请"
          :description="`提交时间：${formatTime(pendingRequest.createdAt)}。审核完成前无法再次提交。`"
        />
        <a-alert
          v-if="role === 'ADMIN'"
          type="info"
          show-icon
          style="margin-bottom: 16px"
          message="管理员账号请使用「用户管理」直接维护用户资料，此处无需提交申请。"
        />
        <a-descriptions v-if="me" bordered size="small" :column="1" title="当前已生效资料">
          <a-descriptions-item label="用户名">{{ me.username }}</a-descriptions-item>
          <a-descriptions-item label="显示名">{{ me.displayName || "—" }}</a-descriptions-item>
          <a-descriptions-item label="角色">{{ me.role }}</a-descriptions-item>
          <a-descriptions-item label="额定周工时">{{ me.baseCapacity ?? "—" }}</a-descriptions-item>
          <a-descriptions-item label="技能 JSON">
            <pre class="skills-preview">{{ prettyJson(me.skillsJson) }}</pre>
          </a-descriptions-item>
        </a-descriptions>

        <div v-if="me && role !== 'ADMIN'" style="margin-top: 16px">
          <a-button type="primary" :disabled="!!pendingRequest" @click="openEditModal">
            修改信息
          </a-button>
          <a-typography-text v-if="pendingRequest" type="secondary" style="margin-left: 12px">
            有待审核申请时不可再次修改
          </a-typography-text>
        </div>
      </a-spin>
    </a-card>

    <a-modal
      v-model:open="editModalOpen"
      title="申请修改个人信息"
      width="640"
      ok-text="提交修改申请"
      cancel-text="取消"
      :confirm-loading="submitting"
      destroy-on-close
      @ok="onSubmit"
      @cancel="editModalOpen = false"
    >
      <a-typography-text type="secondary" style="display: block; margin-bottom: 16px">
        以下为当前已生效资料的副本，可直接在基础上修改；提交后需管理员审核通过才会生效。
      </a-typography-text>
      <a-form layout="vertical">
        <a-form-item label="显示名" required>
          <a-input v-model:value="form.displayName" placeholder="用于界面展示的名称" maxlength="128" show-count />
        </a-form-item>
        <a-form-item label="需求技能与熟练度（0~1）">
          <SkillPickerRows v-model:rows="skillRows" :catalog="catalog" />
        </a-form-item>
        <a-form-item label="额定周工时" required>
          <a-input-number v-model:value="form.baseCapacity" :min="0.5" :max="168" :step="0.5" style="width: 100%" />
        </a-form-item>
        <a-form-item label="申请说明（可选）">
          <a-textarea v-model:value="form.applyReason" :rows="2" placeholder="如修改原因等" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-space>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { message } from "ant-design-vue";
import axios from "axios";
import dayjs from "dayjs";
import client from "../api/client";
import SkillPickerRows from "../components/SkillPickerRows.vue";
import {
  buildSkillsJson,
  parseSkillsJson,
  type SkillCatalogEntry,
  type SkillRow,
} from "../constants/skillTiers";

type Me = {
  id: number;
  username: string;
  displayName?: string;
  role: string;
  skillsJson?: string;
  baseCapacity?: number;
};

type Pending = {
  id: number;
  createdAt?: string;
};

const role = ref(localStorage.getItem("role") || "");
const loading = ref(true);
const submitting = ref(false);
const me = ref<Me | null>(null);
const pendingRequest = ref<Pending | null>(null);
const catalog = ref<SkillCatalogEntry[]>([]);
const skillRows = ref<SkillRow[]>([{ skillCode: "coding", proficiency: 0.75 }]);
const editModalOpen = ref(false);

const form = reactive({
  displayName: "",
  baseCapacity: 40 as number,
  applyReason: "",
});

function prettyJson(s?: string) {
  if (s == null || !String(s).trim()) return "—";
  try {
    return JSON.stringify(JSON.parse(String(s)), null, 2);
  } catch {
    return String(s);
  }
}

function formatTime(s?: string) {
  if (!s) return "";
  return dayjs(s).format("YYYY-MM-DD HH:mm");
}

async function loadCatalog() {
  try {
    const { data } = await client.get<{ skillCode: string; label: string }[]>("/skill-options/catalog");
    catalog.value = data.map((x) => ({ skillCode: x.skillCode, label: x.label }));
  } catch {
    message.error("加载技能目录失败");
  }
}

async function load() {
  loading.value = true;
  try {
    const [meRes, pendRes] = await Promise.all([
      client.get<Me>("/auth/me"),
      client.get<Pending | null>("/profile-requests/me/pending"),
    ]);
    me.value = meRes.data;
    pendingRequest.value = pendRes.data && pendRes.data.id ? pendRes.data : null;
  } catch {
    message.error("加载个人信息失败");
  } finally {
    loading.value = false;
  }
}

function cloneSkillRows(rows: SkillRow[]): SkillRow[] {
  return rows.map((r) => ({
    skillCode: r.skillCode,
    proficiency: r.proficiency,
  }));
}

/** 打开弹窗时用当前已生效资料预填表单 */
function openEditModal() {
  const m = me.value;
  if (!m || role.value === "ADMIN") return;
  if (pendingRequest.value) {
    message.warning("已有待审核申请");
    return;
  }
  form.displayName = (m.displayName && m.displayName.trim()) || "";
  form.baseCapacity = m.baseCapacity != null ? Number(m.baseCapacity) : 40;
  form.applyReason = "";
  const rows = parseSkillsJson(m.skillsJson);
  skillRows.value = rows.length ? cloneSkillRows(rows) : [{ skillCode: "coding", proficiency: 0.75 }];
  editModalOpen.value = true;
}

async function onSubmit() {
  if (role.value === "ADMIN") return Promise.reject();
  if (pendingRequest.value) {
    message.warning("已有待审核申请");
    return Promise.reject();
  }
  const dn = form.displayName?.trim() ?? "";
  if (!dn) {
    message.warning("请填写显示名");
    return Promise.reject();
  }
  submitting.value = true;
  try {
    const skillsJson = buildSkillsJson(skillRows.value);
    await client.post("/profile-requests/submit", {
      displayName: dn,
      skillsJson: skillsJson === "{}" ? "{}" : skillsJson,
      baseCapacity: form.baseCapacity,
      applyReason: form.applyReason?.trim() || undefined,
    });
    message.success("已提交申请，请等待管理员审核；结果将通过消息中心通知");
    editModalOpen.value = false;
    await load();
    window.dispatchEvent(new Event("messages-updated"));
  } catch (e: unknown) {
    const apiMsg = axios.isAxiosError(e) ? String((e.response?.data as { error?: string })?.error ?? "") : "";
    message.error(apiMsg || "提交失败");
    return Promise.reject();
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  loadCatalog().then(() => load());
});
</script>

<style scoped>
.skills-preview {
  margin: 0;
  max-height: 160px;
  overflow: auto;
  font-size: 12px;
  background: rgba(0, 0, 0, 0.04);
  padding: 8px;
  border-radius: 4px;
}
</style>
