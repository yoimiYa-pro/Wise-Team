<template>
  <a-space direction="vertical" style="width: 100%" size="middle">
    <a-card :title="`团队 ${tid} 任务`">
      <a-space wrap>
        <a-button type="primary" @click="open = true">新建任务</a-button>
        <a-button @click="refreshRisks">重算风险</a-button>
      </a-space>
      <a-table
        style="margin-top: 16px"
        :row-key="(r: TaskRow) => r.id"
        :data-source="tasks"
        :columns="columns"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'">
            <a-typography-link @click="openTaskDetail(record)">{{ record.title }}</a-typography-link>
          </template>
          <template v-else-if="column.key === 'progress'">
            <a-space align="center" :size="8" wrap>
              <a-slider
                style="width: 180px; margin: 0"
                :value="
                  progressDraft[record.id] ??
                  (record.status === 'COMPLETED' ? (record.progress ?? 100) : (record.progress ?? 0))
                "
                :min="0"
                :max="record.status === 'COMPLETED' ? 100 : 89"
                :disabled="!record.assigneeId || record.status === 'COMPLETED'"
                :tooltip-open="false"
                @change="(v) => onProgressSliderChange(record.id, v)"
                @after-change="() => commitProgressSlider(record)"
              />
              <span class="progress-percent">{{ displayProgressPercent(record) }}%</span>
            </a-space>
          </template>
          <template v-else-if="column.key === 'risk'">
            <a-tag :color="riskColor(record)">
              {{ record.riskLevel }}
              {{
                record.delayProbability != null
                  ? (Number(record.delayProbability) * 100).toFixed(1) + "%"
                  : ""
              }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'edit'">
            <a-button v-if="canEditTask" size="small" @click="openEditModal(record)">修改</a-button>
            <span v-else class="cell-muted">—</span>
          </template>
          <template v-else-if="column.key === 'assign'">
            <a-button size="small" @click="openAssign(record)">指派</a-button>
          </template>
          <template v-else-if="column.key === 'statusReq'">
            <a-button
              v-if="canApplyStatusChange(record)"
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
    </a-card>

    <a-modal v-model:open="open" title="新建任务" width="640" @ok="onCreate" @cancel="open = false">
      <a-form ref="formRef" :model="createForm" layout="vertical">
        <a-form-item name="title" label="标题" :rules="[{ required: true }]">
          <a-input v-model:value="createForm.title" />
        </a-form-item>
        <a-form-item name="description" label="描述">
          <a-textarea v-model:value="createForm.description" :rows="2" />
        </a-form-item>
        <a-form-item label="难度(1-5)">
          <a-input-number v-model:value="createForm.difficulty" :min="1" :max="5" :step="0.1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="优先级(1-5)">
          <a-input-number v-model:value="createForm.priority" :min="1" :max="5" style="width: 100%" />
        </a-form-item>
        <a-form-item label="预估工时">
          <a-input-number v-model:value="createForm.estHours" :min="0.5" style="width: 100%" />
        </a-form-item>
        <a-form-item label="截止日期">
          <a-date-picker
            v-model:value="createForm.deadline"
            style="width: 100%"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            allow-clear
            placeholder="可选，用于风险与负载预测"
          />
        </a-form-item>
        <a-form-item label="需求技能与熟练度要求">
          <SkillPickerRows v-model:rows="skillRows" :catalog="catalog" />
        </a-form-item>
        <a-form-item label="前置任务">
          <a-select
            v-model:value="createForm.predecessorIds"
            mode="multiple"
            allow-clear
            show-search
            :options="predecessorTaskOptions"
            :filter-option="filterPredecessorOption"
            option-filter-prop="label"
            style="width: 100%"
            placeholder="选择须先完成的同团队任务（可选）"
          />
        </a-form-item>
        <a-form-item label="智能推荐负责人" style="margin-bottom: 0">
          <a-space direction="vertical" style="width: 100%">
            <a-button :loading="recLoading" @click="fetchRecommend">刷新推荐排序</a-button>
            <a-list
              v-if="rec.length"
              class="recommend-list"
              size="small"
              bordered
              :data-source="rec"
            >
              <template #renderItem="{ item }">
                <a-list-item>
                  <template #actions>
                    <a-button
                      type="primary"
                      size="small"
                      :loading="createQuickAssignLoading && createQuickAssignUserId === item.userId"
                      :disabled="item.userId == null"
                      @click="onCreateWithRecommendAssignee(item)"
                    >
                      一键指派
                    </a-button>
                  </template>
                  <a-list-item-meta>
                    <template #title>
                      <span>{{ recommendDisplayName(item) }}</span>
                      <span v-if="recommendUsernameNote(item)" class="recommend-username">{{
                        recommendUsernameNote(item)
                      }}</span>
                    </template>
                    <template #description>
                      <a-space wrap size="small">
                        <a-tag color="blue">综合 {{ formatRecommendScore(item.totalScore) }}</a-tag>
                        <a-tag>技能匹配 {{ formatRecommendRatio(item.skillMatch) }}</a-tag>
                        <a-tag>绩效 {{ formatRecommendRatio(item.performanceNorm) }}</a-tag>
                        <a-tag>负载 {{ formatRecommendRatio(item.loadRatio) }}</a-tag>
                      </a-space>
                    </template>
                  </a-list-item-meta>
                </a-list-item>
              </template>
            </a-list>
            <a-typography-text v-else-if="recFetched" type="secondary">暂无推荐结果</a-typography-text>
          </a-space>
        </a-form-item>
      </a-form>
    </a-modal>

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
          <a-select
            v-model:value="statusApplyForm.toStatus"
            :options="statusApplyTargetOptions"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="说明（可选）">
          <a-textarea v-model:value="statusApplyForm.applyReason" :rows="3" placeholder="进度与变更依据等" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="assignOpen"
      title="指派负责人"
      width="640"
      :confirm-loading="assignSaving"
      ok-text="确定"
      cancel-text="取消"
      @ok="submitAssign"
      @cancel="assignOpen = false"
    >
      <a-form layout="vertical">
        <a-form-item label="智能匹配推荐（按任务需求技能）">
          <a-space direction="vertical" style="width: 100%">
            <a-button size="small" :loading="assignRecLoading" @click="refreshAssignRecommend">重新计算推荐</a-button>
            <a-list
              v-if="assignRec.length"
              class="recommend-list"
              size="small"
              bordered
              :data-source="assignRec"
            >
              <template #renderItem="{ item }">
                <a-list-item>
                  <template #actions>
                    <a-button type="link" size="small" @click="pickAssignRecommendUser(item.userId)">选用此人</a-button>
                  </template>
                  <a-list-item-meta>
                    <template #title>
                      <span>{{ recommendDisplayName(item) }}</span>
                      <span v-if="recommendUsernameNote(item)" class="recommend-username">{{
                        recommendUsernameNote(item)
                      }}</span>
                      <a-tag v-if="assignForm.assigneeId === item.userId" color="green" style="margin-left: 8px"
                        >已选</a-tag
                      >
                    </template>
                    <template #description>
                      <a-space wrap size="small">
                        <a-tag color="blue">综合 {{ formatRecommendScore(item.totalScore) }}</a-tag>
                        <a-tag>技能匹配 {{ formatRecommendRatio(item.skillMatch) }}</a-tag>
                        <a-tag>绩效 {{ formatRecommendRatio(item.performanceNorm) }}</a-tag>
                        <a-tag>负载 {{ formatRecommendRatio(item.loadRatio) }}</a-tag>
                      </a-space>
                    </template>
                  </a-list-item-meta>
                </a-list-item>
              </template>
            </a-list>
            <a-typography-text v-else-if="!assignRecLoading" type="secondary">
              暂无推荐结果（可仍从下方下拉框手动选择）
            </a-typography-text>
          </a-space>
        </a-form-item>
        <a-form-item label="选择成员（显示名 / 用户名）">
          <a-select
            v-model:value="assignForm.assigneeId"
            allow-clear
            show-search
            placeholder="请选择已通过审核的成员"
            :options="assignOptions"
            :filter-option="filterAssignOption"
            style="width: 100%"
          />
        </a-form-item>
        <a-typography-text type="secondary" style="font-size: 12px">
          清空选择可取消指派；列表仅含本团队已批准成员。
        </a-typography-text>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="detailOpen"
      :title="detailModalTitle"
      width="720"
      destroy-on-close
      :footer="null"
      @cancel="clearTaskDetail"
    >
      <a-spin :spinning="detailLoading">
        <template v-if="detailTask">
          <a-descriptions bordered size="small" :column="1" :label-style="{ width: '140px' }">
            <a-descriptions-item label="任务 ID">{{ detailTask.id }}</a-descriptions-item>
            <a-descriptions-item label="标题">{{ detailTask.title ?? "—" }}</a-descriptions-item>
            <a-descriptions-item label="描述">
              <span class="detail-multiline">{{ detailTask.description?.trim() ? detailTask.description : "—" }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="状态">{{ detailTask.status ?? "—" }}</a-descriptions-item>
            <a-descriptions-item label="进度">{{ detailTask.progress ?? 0 }}%</a-descriptions-item>
            <a-descriptions-item label="难度 / 优先级">
              {{ detailTask.difficulty ?? "—" }} / {{ detailTask.priority ?? "—" }}
            </a-descriptions-item>
            <a-descriptions-item label="预估工时">{{ detailTask.estHours ?? "—" }}</a-descriptions-item>
            <a-descriptions-item label="截止日期">{{ formatDetailDeadline(detailTask.deadline) }}</a-descriptions-item>
            <a-descriptions-item label="创建人 ID">{{ detailTask.creatorId ?? "—" }}</a-descriptions-item>
            <a-descriptions-item label="负责人">{{ assigneeDetailLabel(detailTask.assigneeId) }}</a-descriptions-item>
            <a-descriptions-item label="需求技能 JSON">
              <pre class="detail-json">{{ prettySkillsJson(detailTask.requiredSkillsJson) }}</pre>
            </a-descriptions-item>
            <a-descriptions-item label="前置任务">
              <ul v-if="detailPredecessors.length" class="detail-pred-list">
                <li v-for="p in detailPredecessors" :key="p.id">{{ formatPredecessorLine(p) }}</li>
              </ul>
              <span v-else>无</span>
            </a-descriptions-item>
            <template v-if="canEditTask">
              <a-descriptions-item label="编辑前置" :span="1">
                <a-space direction="vertical" style="width: 100%">
                  <a-select
                    v-model:value="detailPredSelectedIds"
                    mode="multiple"
                    style="width: 100%"
                    placeholder="选择本团队内的前置任务（须为团队管理者）"
                    :options="detailPredecessorSelectOptions"
                    :filter-option="filterPredecessorOption"
                    show-search
                    allow-clear
                  />
                  <a-button type="primary" :loading="detailDepsSaving" @click="saveDetailDependencies">
                    保存前置依赖
                  </a-button>
                </a-space>
              </a-descriptions-item>
            </template>
            <a-descriptions-item label="风险">
              <a-tag :color="riskTagColor(detailTask.riskLevel)">
                {{ detailTask.riskLevel ?? "—" }}
                {{
                  detailTask.delayProbability != null
                    ? (Number(detailTask.delayProbability) * 100).toFixed(1) + "%"
                    : ""
                }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="版本号">{{ detailTask.version ?? "—" }}</a-descriptions-item>
            <a-descriptions-item label="创建时间">{{ formatDetailInstant(detailTask.createdAt) }}</a-descriptions-item>
            <a-descriptions-item label="更新时间">{{ formatDetailInstant(detailTask.updatedAt) }}</a-descriptions-item>
          </a-descriptions>
          <div style="margin-top: 16px; text-align: right">
            <a-button type="primary" @click="clearTaskDetail">关闭</a-button>
          </div>
        </template>
      </a-spin>
    </a-modal>

    <a-modal
      v-model:open="editModalOpen"
      title="修改任务"
      width="640"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="editSaving"
      destroy-on-close
      @ok="submitEdit"
      @cancel="closeEditModal"
    >
      <a-spin :spinning="editLoading">
        <a-form layout="vertical">
          <a-form-item label="标题" required>
            <a-input v-model:value="editForm.title" maxlength="255" show-count />
          </a-form-item>
          <a-form-item label="描述">
            <a-textarea v-model:value="editForm.description" :rows="2" />
          </a-form-item>
          <a-form-item label="状态">
            <a-select v-model:value="editForm.status" :options="taskEditStatusOptions" style="width: 100%" />
          </a-form-item>
          <a-form-item label="难度(1-5)">
            <a-input-number v-model:value="editForm.difficulty" :min="1" :max="5" :step="0.1" style="width: 100%" />
          </a-form-item>
          <a-form-item label="优先级(1-5)">
            <a-input-number v-model:value="editForm.priority" :min="1" :max="5" style="width: 100%" />
          </a-form-item>
          <a-form-item label="预估工时">
            <a-input-number v-model:value="editForm.estHours" :min="0.5" style="width: 100%" />
          </a-form-item>
          <a-form-item label="截止日期">
            <a-date-picker
              v-model:value="editForm.deadline"
              style="width: 100%"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              allow-clear
              placeholder="可选"
            />
          </a-form-item>
          <a-form-item label="需求技能与熟练度要求">
            <SkillPickerRows v-model:rows="editSkillRows" :catalog="catalog" />
          </a-form-item>
          <a-typography-text type="secondary" style="font-size: 12px">
            负责人请在列表中通过「指派」调整；前置任务请在任务详情弹窗中维护。当前进度仍可在表格中拖动。
          </a-typography-text>
        </a-form>
      </a-spin>
    </a-modal>
  </a-space>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import type { FormInstance } from "ant-design-vue";
import { message } from "ant-design-vue";
import axios from "axios";
import client from "../api/client";
import SkillPickerRows from "../components/SkillPickerRows.vue";
import {
  buildSkillsJson,
  parseSkillsJson,
  type SkillCatalogEntry,
  type SkillRow,
} from "../constants/skillTiers";
import { canApplyTaskStatus, nextTaskStatusOptions } from "../constants/taskStatusFlow";

/** Slider 单柄时值；受控模式下须用 @change 同步草稿，否则 @after-change 可能为旧值 */
type SliderValue = number | [number, number];

type TaskRow = {
  id: number;
  title: string;
  status: string;
  progress: number;
  assigneeId?: number;
  version: number;
  riskLevel?: string;
  delayProbability?: number;
  /** 任务需求技能 JSON，用于指派时智能推荐 */
  requiredSkillsJson?: string;
};

/** GET /tasks/:id 返回的完整字段 */
type TaskDetailVO = {
  id: number;
  teamId?: number;
  creatorId?: number;
  assigneeId?: number;
  title?: string;
  description?: string;
  difficulty?: number;
  priority?: number;
  estHours?: number;
  deadline?: string;
  progress?: number;
  status?: string;
  requiredSkillsJson?: string;
  riskLevel?: string;
  delayProbability?: number;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
};

const route = useRoute();
const tid = computed(() => Number(route.params.teamId));
const currentUserId = computed(() => Number(localStorage.getItem("userId")) || 0);

const canEditTask = computed(() => {
  const r = localStorage.getItem("role");
  return r === "ADMIN" || r === "MANAGER";
});

const taskEditStatusOptions = [
  { value: "CREATED", label: "已创建 (CREATED)" },
  { value: "IN_PROGRESS", label: "进行中 (IN_PROGRESS)" },
  { value: "SUSPENDED", label: "暂停 (SUSPENDED)" },
  { value: "COMPLETED", label: "已完成 (COMPLETED)" },
  { value: "ARCHIVED", label: "已归档 (ARCHIVED)" },
];

const tasks = ref<TaskRow[]>([]);
const progressDraft = ref<Record<number, number>>({});
const open = ref(false);
const formRef = ref<FormInstance>();
type RecommendRow = {
  userId?: number;
  username?: string;
  displayName?: string;
  totalScore?: number;
  skillMatch?: number;
  performanceNorm?: number;
  loadRatio?: number;
};

const rec = ref<RecommendRow[]>([]);
const recLoading = ref(false);
const recFetched = ref(false);
const createQuickAssignLoading = ref(false);
const createQuickAssignUserId = ref<number | null>(null);

const detailOpen = ref(false);
const detailLoading = ref(false);
const detailTask = ref<TaskDetailVO | null>(null);
const detailPredecessors = ref<TaskRow[]>([]);
const detailUsers = ref<{ userId: number; username: string; displayName?: string }[]>([]);
/** 详情弹窗内编辑的前置任务 id（与多选同步） */
const detailPredSelectedIds = ref<number[]>([]);
const detailDepsSaving = ref(false);

const detailPredecessorSelectOptions = computed(() => {
  const selfId = detailTask.value?.id;
  return tasks.value
    .filter((t) => t.id !== selfId)
    .map((t) => ({ value: t.id, label: formatPredecessorTaskLabel(t) }));
});

const detailModalTitle = computed(() =>
  detailTask.value ? `任务详情 #${detailTask.value.id}` : "任务详情"
);

const statusApplyOpen = ref(false);
const statusApplySaving = ref(false);
const statusApplyTask = ref<TaskRow | null>(null);
const statusApplyForm = reactive<{ toStatus: string; applyReason: string }>({ toStatus: "", applyReason: "" });

const statusApplyTargetOptions = computed(() => {
  const r = statusApplyTask.value;
  if (!r) return [];
  return nextTaskStatusOptions(r.status);
});

function canApplyStatusChange(r: TaskRow): boolean {
  if (!r.assigneeId || r.assigneeId !== currentUserId.value) return false;
  if (!canApplyTaskStatus(r.status)) return false;
  return nextTaskStatusOptions(r.status).length > 0;
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

const assignOpen = ref(false);
const assignSaving = ref(false);
const assignTask = ref<TaskRow | null>(null);
const assignForm = reactive<{ assigneeId?: number }>({});
const assignableUsers = ref<{ userId: number; username: string; displayName?: string }[]>([]);
const assignRec = ref<RecommendRow[]>([]);
const assignRecLoading = ref(false);

const catalog = ref<SkillCatalogEntry[]>([]);
const skillRows = ref<SkillRow[]>([{ skillCode: "coding", proficiency: 0.85 }]);

const editModalOpen = ref(false);
const editLoading = ref(false);
const editSaving = ref(false);
const editSnap = ref<TaskDetailVO | null>(null);
const editSkillRows = ref<SkillRow[]>([{ skillCode: "coding", proficiency: 0.85 }]);
const editForm = reactive({
  title: "",
  description: "",
  status: "CREATED",
  difficulty: 2 as number,
  priority: 3 as number,
  estHours: 8 as number,
  deadline: null as string | null,
});

const createForm = reactive({
  title: "",
  description: "",
  difficulty: 2,
  priority: 3,
  estHours: 8,
  deadline: null as string | null,
  predecessorIds: [] as number[],
});

const predecessorTaskOptions = computed(() =>
  tasks.value.map((t) => ({
    value: t.id,
    label: formatPredecessorTaskLabel(t),
  }))
);

function formatPredecessorTaskLabel(t: TaskRow) {
  const title = (t.title || "（无标题）").trim();
  const max = 48;
  const short = title.length > max ? title.slice(0, max) + "…" : title;
  return `${short} (#${t.id})`;
}

function filterPredecessorOption(input: string, option: { label?: string }) {
  return (option.label ?? "").toLowerCase().includes(input.toLowerCase());
}

const assignOptions = computed(() =>
  assignableUsers.value.map((u) => ({
    value: u.userId,
    label: formatMemberLabel(u),
  }))
);

function formatMemberLabel(u: { username: string; displayName?: string }) {
  const name = (u.displayName && u.displayName.trim()) || u.username;
  if (name === u.username) return u.username;
  return `${name}（${u.username}）`;
}

function filterAssignOption(input: string, option: { label?: string }) {
  return (option.label ?? "").toLowerCase().includes(input.toLowerCase());
}

const columns = [
  { title: "标题", dataIndex: "title", key: "title" },
  { title: "状态", dataIndex: "status", key: "status" },
  { title: "进度", key: "progress" },
  { title: "风险", key: "risk" },
  { title: "修改", key: "edit", width: 72 },
  { title: "指派", key: "assign" },
  { title: "状态申请", key: "statusReq", width: 100 },
];

async function loadCatalog() {
  try {
    const { data } = await client.get<{ skillCode: string; label: string }[]>("/skill-options/catalog");
    catalog.value = data.map((x) => ({ skillCode: x.skillCode, label: x.label }));
  } catch {
    message.error("加载技能目录失败");
  }
}

async function load() {
  try {
    const { data } = await client.get<TaskRow[]>(`/tasks/teams/${tid.value}`);
    tasks.value = data;
  } catch {
    message.error("加载任务失败");
  }
}

onMounted(() => {
  loadCatalog();
  if (tid.value) load();
});

watch(open, (isOpen) => {
  if (isOpen && tid.value) load();
});

function riskTagColor(level?: string) {
  if (level === "RED") return "red";
  if (level === "ORANGE") return "orange";
  return "green";
}

function riskColor(r: TaskRow) {
  return riskTagColor(r.riskLevel);
}

async function saveDetailDependencies() {
  const t = detailTask.value;
  if (!t) return;
  detailDepsSaving.value = true;
  try {
    const ids = (detailPredSelectedIds.value ?? []).filter((n) => n != null && !Number.isNaN(Number(n)));
    await client.put(`/tasks/${t.id}/dependencies`, { predecessorIds: ids });
    message.success("前置依赖已更新");
    const { data } = await client.get<TaskRow[]>(`/tasks/${t.id}/predecessors`);
    detailPredecessors.value = Array.isArray(data) ? data : [];
    detailPredSelectedIds.value = detailPredecessors.value.map((p) => p.id);
    await load();
  } catch (e: unknown) {
    const apiMsg = axios.isAxiosError(e) ? String((e.response?.data as { error?: string })?.error ?? "") : "";
    message.error(apiMsg || "保存失败");
  } finally {
    detailDepsSaving.value = false;
  }
}

function clearTaskDetail() {
  detailOpen.value = false;
  detailTask.value = null;
  detailPredecessors.value = [];
  detailUsers.value = [];
  detailPredSelectedIds.value = [];
}

function closeEditModal() {
  editModalOpen.value = false;
  editSnap.value = null;
}

async function openEditModal(r: TaskRow) {
  if (!canEditTask.value) return;
  editModalOpen.value = true;
  editLoading.value = true;
  editSnap.value = null;
  try {
    const { data } = await client.get<TaskDetailVO>(`/tasks/${r.id}`);
    editSnap.value = data;
    editForm.title = data.title ?? "";
    editForm.description = data.description ?? "";
    editForm.status = data.status ?? "CREATED";
    editForm.difficulty = data.difficulty != null ? Number(data.difficulty) : 2;
    editForm.priority = data.priority != null ? Number(data.priority) : 3;
    editForm.estHours = data.estHours != null ? Number(data.estHours) : 8;
    editForm.deadline = data.deadline ? String(data.deadline).slice(0, 10) : null;
    const rows = parseSkillsJson(data.requiredSkillsJson);
    editSkillRows.value = rows.length ? rows.map((x) => ({ ...x })) : [{ skillCode: "coding", proficiency: 0.85 }];
  } catch {
    message.error("加载任务失败");
    editModalOpen.value = false;
  } finally {
    editLoading.value = false;
  }
}

async function submitEdit() {
  const t = editSnap.value;
  if (!t) return Promise.reject();
  const title = editForm.title?.trim() ?? "";
  if (!title) {
    message.warning("请填写标题");
    return Promise.reject();
  }
  editSaving.value = true;
  try {
    let reqSkills = buildSkillsJson(editSkillRows.value);
    if (reqSkills === "{}") reqSkills = '{"coding":0.8}';
    await client.put(`/tasks/${t.id}`, {
      id: t.id,
      teamId: t.teamId,
      creatorId: t.creatorId,
      assigneeId: t.assigneeId ?? null,
      title,
      description: editForm.description?.trim() ?? "",
      difficulty: editForm.difficulty ?? 2,
      priority: editForm.priority ?? 3,
      estHours: editForm.estHours ?? 8,
      deadline: editForm.deadline || null,
      progress: t.progress ?? 0,
      status: editForm.status,
      requiredSkillsJson: reqSkills,
      version: t.version,
    });
    message.success("已保存");
    closeEditModal();
    load();
  } catch (e: unknown) {
    const apiMsg = axios.isAxiosError(e) ? String((e.response?.data as { error?: string })?.error ?? "") : "";
    message.error(apiMsg || "保存失败（可能版本冲突，请刷新后重试）");
    return Promise.reject();
  } finally {
    editSaving.value = false;
  }
}

async function openTaskDetail(r: TaskRow) {
  detailOpen.value = true;
  detailLoading.value = true;
  detailTask.value = null;
  detailPredecessors.value = [];
  detailUsers.value = [];
  try {
    const [tRes, pRes] = await Promise.all([
      client.get<TaskDetailVO>(`/tasks/${r.id}`),
      client.get<TaskRow[]>(`/tasks/${r.id}/predecessors`),
    ]);
    detailTask.value = tRes.data;
    detailPredecessors.value = Array.isArray(pRes.data) ? pRes.data : [];
    detailPredSelectedIds.value = detailPredecessors.value.map((p) => p.id);
    try {
      const uRes = await client.get<{ userId: number; username: string; displayName?: string }[]>(
        `/teams/${tid.value}/assignable-users`
      );
      detailUsers.value = Array.isArray(uRes.data) ? uRes.data : [];
    } catch {
      detailUsers.value = [];
    }
  } catch {
    message.error("加载任务详情失败");
    clearTaskDetail();
  } finally {
    detailLoading.value = false;
  }
}

function assigneeDetailLabel(uid?: number | null) {
  if (uid == null) return "未指派";
  const u = detailUsers.value.find((x) => x.userId === uid);
  if (!u) return `用户 ID ${uid}`;
  return formatMemberLabel(u);
}

function prettySkillsJson(s?: string | null) {
  if (s == null || !String(s).trim()) return "—";
  try {
    return JSON.stringify(JSON.parse(String(s)), null, 2);
  } catch {
    return String(s);
  }
}

function formatPredecessorLine(p: TaskRow) {
  const t = (p.title || "（无标题）").trim();
  return `${t} (#${p.id}) — ${p.status ?? "—"}`;
}

function formatDetailDeadline(d?: string | null) {
  if (d == null || !String(d).trim()) return "—";
  return String(d).slice(0, 10);
}

function formatDetailInstant(s?: string | null) {
  if (s == null || !String(s).trim()) return "—";
  return String(s).replace("T", " ").slice(0, 19);
}

/** 与滑块当前值一致，用于在滑块旁显示百分比（含拖动中的草稿） */
function displayProgressPercent(r: TaskRow): number {
  const d = progressDraft.value[r.id];
  if (d !== undefined) return d;
  if (r.status === "COMPLETED") return r.progress ?? 100;
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

async function onProgress(r: TaskRow, p: number) {
  try {
    await client.patch(`/tasks/${r.id}/progress`, { progress: p, version: r.version });
    message.success("进度已更新");
    await load();
  } catch (e: unknown) {
    const apiMsg = axios.isAxiosError(e) ? String((e.response?.data as { error?: string })?.error ?? "") : "";
    message.error(apiMsg || "更新失败");
    await load();
  } finally {
    const next = { ...progressDraft.value };
    delete next[r.id];
    progressDraft.value = next;
  }
}

async function loadAssignableUsers() {
  try {
    const { data } = await client.get<{ userId: number; username: string; displayName?: string }[]>(
      `/teams/${tid.value}/assignable-users`
    );
    assignableUsers.value = data;
  } catch {
    message.error("加载可指派成员失败");
    assignableUsers.value = [];
  }
}

function skillsJsonForTaskAssign(t: TaskRow | null): string {
  if (!t) return '{"coding":0.8}';
  const raw = t.requiredSkillsJson?.trim();
  if (raw && raw !== "{}") return raw;
  return '{"coding":0.8}';
}

async function fetchAssignRecommend(t: TaskRow) {
  assignRecLoading.value = true;
  try {
    const { data } = await client.get<RecommendRow[]>(`/teams/${tid.value}/assign/recommend`, {
      params: { requiredSkillsJson: skillsJsonForTaskAssign(t) },
    });
    assignRec.value = Array.isArray(data) ? data : [];
  } catch {
    assignRec.value = [];
    message.error("加载智能推荐失败");
  } finally {
    assignRecLoading.value = false;
  }
}

function refreshAssignRecommend() {
  const t = assignTask.value;
  if (t) void fetchAssignRecommend(t);
}

function pickAssignRecommendUser(userId?: number) {
  if (userId == null || Number.isNaN(Number(userId))) {
    message.warning("无效的推荐成员");
    return;
  }
  assignForm.assigneeId = Number(userId);
}

watch(assignOpen, (open) => {
  if (!open) {
    assignRec.value = [];
  }
});

async function openAssign(r: TaskRow) {
  assignTask.value = r;
  assignForm.assigneeId = r.assigneeId;
  assignRec.value = [];
  await loadAssignableUsers();
  assignOpen.value = true;
  await fetchAssignRecommend(r);
}

async function submitAssign() {
  const r = assignTask.value;
  if (!r) return;
  assignSaving.value = true;
  try {
    await client.patch(`/tasks/${r.id}/reassign`, {
      assigneeId: assignForm.assigneeId ?? null,
      version: r.version,
    });
    message.success("已更新指派");
    assignOpen.value = false;
    assignTask.value = null;
    load();
  } catch {
    message.error("指派失败，请刷新后重试");
    return Promise.reject();
  } finally {
    assignSaving.value = false;
  }
}

async function refreshRisks() {
  await client.post(`/tasks/teams/${tid.value}/risks/refresh`);
  load();
}

function skillsJsonForRecommend(): string {
  const j = buildSkillsJson(skillRows.value);
  return j === "{}" ? '{"coding":0.8}' : j;
}

function recommendDisplayName(row: RecommendRow) {
  const dn = row.displayName?.trim();
  if (dn) return dn;
  if (row.username?.trim()) return row.username;
  return row.userId != null ? `用户 #${row.userId}` : "未知成员";
}

function recommendUsernameNote(row: RecommendRow) {
  const dn = row.displayName?.trim();
  const un = row.username?.trim();
  if (dn && un && dn !== un) return `（${un}）`;
  return "";
}

function formatRecommendRatio(n: unknown) {
  const x = Number(n);
  if (Number.isNaN(x)) return "—";
  return (x * 100).toFixed(1) + "%";
}

function formatRecommendScore(n: unknown) {
  const x = Number(n);
  if (Number.isNaN(x)) return "—";
  return x.toFixed(4);
}

async function fetchRecommend() {
  recLoading.value = true;
  try {
    const { data } = await client.get<RecommendRow[]>(`/teams/${tid.value}/assign/recommend`, {
      params: { requiredSkillsJson: skillsJsonForRecommend() },
    });
    rec.value = Array.isArray(data) ? data : [];
    recFetched.value = true;
    message.info(rec.value.length ? "已刷新推荐排序" : "暂无符合条件的成员");
  } catch {
    message.error("推荐请求失败");
  } finally {
    recLoading.value = false;
  }
}

function resetCreateModalAfterSuccess() {
  open.value = false;
  formRef.value?.resetFields();
  rec.value = [];
  recFetched.value = false;
  createForm.predecessorIds = [];
  createForm.difficulty = 2;
  createForm.priority = 3;
  createForm.estHours = 8;
  createForm.deadline = null;
  skillRows.value = [{ skillCode: "coding", proficiency: 0.85 }];
  load();
}

async function onCreate() {
  try {
    await formRef.value?.validateFields(["title"]);
    const predecessorIds = createForm.predecessorIds.filter((n) => n != null && !Number.isNaN(Number(n)));
    const reqSkills = buildSkillsJson(skillRows.value);
    await client.post(`/tasks/teams/${tid.value}`, {
      task: {
        title: createForm.title,
        description: createForm.description,
        difficulty: createForm.difficulty ?? 2,
        priority: createForm.priority ?? 3,
        estHours: createForm.estHours ?? 8,
        deadline: createForm.deadline || null,
        status: "CREATED",
        progress: 0,
        requiredSkillsJson: reqSkills === "{}" ? '{"coding":0.8}' : reqSkills,
      },
      predecessorIds,
    });
    message.success("已创建");
    resetCreateModalAfterSuccess();
  } catch {
    return Promise.reject();
  }
}

async function onCreateWithRecommendAssignee(row: RecommendRow) {
  const uid = row.userId != null ? Number(row.userId) : NaN;
  if (Number.isNaN(uid)) {
    message.warning("无法识别该成员的 ID");
    return;
  }
  try {
    await formRef.value?.validateFields(["title"]);
  } catch {
    return;
  }
  createQuickAssignLoading.value = true;
  createQuickAssignUserId.value = uid;
  try {
    const predecessorIds = createForm.predecessorIds.filter((n) => n != null && !Number.isNaN(Number(n)));
    const reqSkills = buildSkillsJson(skillRows.value);
    await client.post(`/tasks/teams/${tid.value}`, {
      task: {
        title: createForm.title,
        description: createForm.description,
        difficulty: createForm.difficulty ?? 2,
        priority: createForm.priority ?? 3,
        estHours: createForm.estHours ?? 8,
        deadline: createForm.deadline || null,
        status: "CREATED",
        progress: 0,
        assigneeId: uid,
        requiredSkillsJson: reqSkills === "{}" ? '{"coding":0.8}' : reqSkills,
      },
      predecessorIds,
    });
    message.success(`已创建并已指派给 ${recommendDisplayName(row)}`);
    resetCreateModalAfterSuccess();
  } catch {
    message.error("一键指派失败，请稍后重试");
  } finally {
    createQuickAssignLoading.value = false;
    createQuickAssignUserId.value = null;
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

.recommend-list {
  margin-top: 4px;
  max-height: 220px;
  overflow: auto;
}

.recommend-username {
  margin-left: 6px;
  font-size: 12px;
  font-weight: normal;
  color: rgba(0, 0, 0, 0.45);
}

.detail-multiline {
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-json {
  margin: 0;
  max-height: 200px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.4;
  background: rgba(0, 0, 0, 0.04);
  padding: 8px;
  border-radius: 4px;
}

.detail-pred-list {
  margin: 0;
  padding-left: 20px;
}
</style>
