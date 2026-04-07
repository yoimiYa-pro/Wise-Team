<template>
  <div v-if="!allowed"></div>
  <a-space v-else direction="vertical" style="width: 100%" size="large">
    <a-typography-paragraph type="secondary" style="margin: 0">
      管理员维护<strong>全局</strong>技能代码；团队管理者可为所管团队增加<strong>团队专属</strong>项。同名代码在合并列表中以团队展示名为准。
    </a-typography-paragraph>

    <a-card v-if="isAdmin" title="全局技能可选项（管理员）">
      <a-button type="primary" @click="openGlobalCreate">新建全局项</a-button>
      <a-table
        style="margin-top: 12px"
        :row-key="(r: SkillOptionRow) => r.id"
        :data-source="globalRows"
        :columns="globalColumns"
        :pagination="false"
        :loading="loadingGlobal"
      />
    </a-card>

    <a-card v-else title="全局技能可选项（只读）">
      <a-table
        :row-key="(r: SkillOptionRow) => r.id"
        :data-source="globalRows"
        :columns="globalReadonlyColumns"
        :pagination="false"
        :loading="loadingGlobal"
      />
    </a-card>

    <a-card title="团队技能可选项">
      <a-space wrap>
        <span>选择团队：</span>
        <a-select
          v-model:value="selectedTeamId"
          style="width: 280px"
          :options="teamSelectOptions"
          placeholder="请选择团队"
          allow-clear
          @change="loadTeamOptions"
        />
        <a-button v-if="selectedTeamId" type="primary" @click="openTeamCreate">新建团队项</a-button>
      </a-space>
      <a-table
        v-if="selectedTeamId"
        style="margin-top: 12px"
        :row-key="(r: SkillOptionRow) => r.id"
        :data-source="teamRows"
        :columns="teamColumns"
        :pagination="false"
        :loading="loadingTeam"
      />
    </a-card>

    <a-modal
      v-model:open="globalModalOpen"
      :title="globalEditingId ? '编辑全局技能' : '新建全局技能'"
      :confirm-loading="saving"
      @ok="submitGlobal"
    >
      <a-form layout="vertical">
        <a-form-item label="技能代码（小写开头，如 coding）" required>
          <a-input v-model:value="globalForm.skillCode" placeholder="coding" />
        </a-form-item>
        <a-form-item label="展示名称" required>
          <a-input v-model:value="globalForm.label" />
        </a-form-item>
        <a-form-item label="排序（越小越靠前）">
          <a-input-number v-model:value="globalForm.sortOrder" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="teamModalOpen"
      :title="teamEditingId ? '编辑团队技能' : '新建团队技能'"
      :confirm-loading="saving"
      @ok="submitTeam"
    >
      <a-form layout="vertical">
        <a-form-item label="技能代码" required>
          <a-input v-model:value="teamForm.skillCode" />
        </a-form-item>
        <a-form-item label="展示名称" required>
          <a-input v-model:value="teamForm.label" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="teamForm.sortOrder" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-space>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { message } from "ant-design-vue";
import client from "../api/client";

type SkillOptionRow = {
  id: number;
  teamId: number;
  skillCode: string;
  label: string;
  sortOrder: number;
};

type TeamLite = { id: number; name: string };

const router = useRouter();
const role = ref(localStorage.getItem("role") || "");
const allowed = computed(() => role.value === "ADMIN" || role.value === "MANAGER");
const isAdmin = computed(() => role.value === "ADMIN");

const globalRows = ref<SkillOptionRow[]>([]);
const teamRows = ref<SkillOptionRow[]>([]);
const teams = ref<TeamLite[]>([]);
const selectedTeamId = ref<number | undefined>(undefined);
const loadingGlobal = ref(false);
const loadingTeam = ref(false);
const saving = ref(false);

const globalModalOpen = ref(false);
const globalEditingId = ref<number | null>(null);
const globalForm = reactive({ skillCode: "", label: "", sortOrder: 0 });

const teamModalOpen = ref(false);
const teamEditingId = ref<number | null>(null);
const teamForm = reactive({ skillCode: "", label: "", sortOrder: 0 });

const teamSelectOptions = computed(() =>
  teams.value.map((t) => ({ value: t.id, label: `${t.name}（#${t.id}）` }))
);

const globalReadonlyColumns = [
  { title: "代码", dataIndex: "skillCode", width: 140 },
  { title: "展示名", dataIndex: "label" },
  { title: "排序", dataIndex: "sortOrder", width: 80 },
];

const globalColumns = [
  ...globalReadonlyColumns,
  {
    title: "操作",
    key: "act",
    width: 160,
    customRender: ({ record }: { record: SkillOptionRow }) =>
      h("span", [
        h(
          "a",
          {
            style: { marginRight: "12px" },
            onClick: () => openGlobalEdit(record),
          },
          "编辑"
        ),
        h(
          "a",
          { style: { color: "#cf1322" }, onClick: () => removeGlobal(record) },
          "删除"
        ),
      ]),
  },
];

const teamColumns = [
  { title: "代码", dataIndex: "skillCode", width: 140 },
  { title: "展示名", dataIndex: "label" },
  { title: "排序", dataIndex: "sortOrder", width: 80 },
  {
    title: "操作",
    key: "act",
    width: 160,
    customRender: ({ record }: { record: SkillOptionRow }) =>
      h("span", [
        h(
          "a",
          {
            style: { marginRight: "12px" },
            onClick: () => openTeamEdit(record),
          },
          "编辑"
        ),
        h(
          "a",
          { style: { color: "#cf1322" }, onClick: () => removeTeam(record) },
          "删除"
        ),
      ]),
  },
];

async function loadGlobal() {
  loadingGlobal.value = true;
  try {
    const url = isAdmin.value ? "/admin/skill-options" : "/skill-options/global";
    const { data } = await client.get<SkillOptionRow[]>(url);
    globalRows.value = data;
  } catch {
    message.error("加载全局技能失败");
  } finally {
    loadingGlobal.value = false;
  }
}

async function loadTeams() {
  try {
    if (isAdmin.value) {
      const { data } = await client.get<TeamLite[]>("/admin/teams");
      teams.value = data;
    } else {
      const { data } = await client.get<TeamLite[]>("/teams/managed");
      teams.value = data;
    }
    if (!selectedTeamId.value && teams.value.length) {
      selectedTeamId.value = teams.value[0].id;
      await loadTeamOptions();
    }
  } catch {
    message.error("加载团队列表失败");
  }
}

async function loadTeamOptions() {
  if (!selectedTeamId.value) {
    teamRows.value = [];
    return;
  }
  loadingTeam.value = true;
  try {
    const { data } = await client.get<SkillOptionRow[]>(`/teams/${selectedTeamId.value}/skill-options`);
    teamRows.value = data;
  } catch {
    message.error("加载团队技能失败");
  } finally {
    loadingTeam.value = false;
  }
}

function openGlobalCreate() {
  globalEditingId.value = null;
  globalForm.skillCode = "";
  globalForm.label = "";
  globalForm.sortOrder = 0;
  globalModalOpen.value = true;
}

function openGlobalEdit(r: SkillOptionRow) {
  globalEditingId.value = r.id;
  globalForm.skillCode = r.skillCode;
  globalForm.label = r.label;
  globalForm.sortOrder = r.sortOrder ?? 0;
  globalModalOpen.value = true;
}

async function submitGlobal() {
  saving.value = true;
  try {
    if (globalEditingId.value) {
      await client.put(`/admin/skill-options/${globalEditingId.value}`, { ...globalForm });
      message.success("已更新");
    } else {
      await client.post("/admin/skill-options", { ...globalForm });
      message.success("已创建");
    }
    globalModalOpen.value = false;
    loadGlobal();
  } catch {
    message.error("保存失败");
  } finally {
    saving.value = false;
  }
}

async function removeGlobal(r: SkillOptionRow) {
  if (!confirm(`删除全局技能「${r.label}」？`)) return;
  try {
    await client.delete(`/admin/skill-options/${r.id}`);
    message.success("已删除");
    loadGlobal();
  } catch {
    message.error("删除失败");
  }
}

function openTeamCreate() {
  if (!selectedTeamId.value) return;
  teamEditingId.value = null;
  teamForm.skillCode = "";
  teamForm.label = "";
  teamForm.sortOrder = 0;
  teamModalOpen.value = true;
}

function openTeamEdit(r: SkillOptionRow) {
  teamEditingId.value = r.id;
  teamForm.skillCode = r.skillCode;
  teamForm.label = r.label;
  teamForm.sortOrder = r.sortOrder ?? 0;
  teamModalOpen.value = true;
}

async function submitTeam() {
  if (!selectedTeamId.value) return;
  saving.value = true;
  try {
    const tid = selectedTeamId.value;
    if (teamEditingId.value) {
      await client.put(`/teams/${tid}/skill-options/${teamEditingId.value}`, { ...teamForm });
      message.success("已更新");
    } else {
      await client.post(`/teams/${tid}/skill-options`, { ...teamForm });
      message.success("已创建");
    }
    teamModalOpen.value = false;
    loadTeamOptions();
  } catch {
    message.error("保存失败");
  } finally {
    saving.value = false;
  }
}

async function removeTeam(r: SkillOptionRow) {
  if (!selectedTeamId.value) return;
  if (!confirm(`删除团队技能「${r.label}」？`)) return;
  try {
    await client.delete(`/teams/${selectedTeamId.value}/skill-options/${r.id}`);
    message.success("已删除");
    loadTeamOptions();
  } catch {
    message.error("删除失败");
  }
}

onMounted(() => {
  if (!allowed.value) {
    router.replace("/");
    return;
  }
  loadGlobal();
  loadTeams();
});
</script>
