<template>
  <div v-if="role !== 'ADMIN'"></div>
  <a-card v-else>
    <div class="audit-toolbar">
      <a-input-search
        v-model:value="keyword"
        placeholder="中文动作名、英文代码、资源类型或详情"
        allow-clear
        class="audit-search"
        @search="load"
      />
    </div>
    <a-table
      :row-key="(r: LogRow) => r.id"
      :data-source="rows"
      :columns="columns"
      :loading="loading"
      :pagination="tablePagination"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <a-tooltip :title="`代码：${record.action}`">
            <a-tag>{{ actionLabel(record.action) }}</a-tag>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'resource'">
          <a-tooltip
            :title="record.resourceType ? `英文类型：${record.resourceType}` : '无资源类型'"
          >
            <span class="audit-cell-text">{{ resourceLabel(record.resourceType) }}</span>
          </a-tooltip>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { message } from "ant-design-vue";
import client from "../api/client";
import { actionLabel, resourceLabel } from "../constants/auditLabels";

type LogRow = {
  id: number;
  actorId?: number;
  action: string;
  resourceType?: string;
  resourceId?: number;
  detail?: string;
};

const router = useRouter();
const role = ref(localStorage.getItem("role") || "");
const rows = ref<LogRow[]>([]);
const keyword = ref("");
const loading = ref(false);

const columns = [
  { title: "ID", dataIndex: "id", width: 64 },
  { title: "操作者", dataIndex: "actorId", width: 88 },
  { title: "动作", dataIndex: "action", key: "action", width: 128, ellipsis: true },
  { title: "资源", dataIndex: "resourceType", key: "resource", width: 112, ellipsis: true },
  { title: "资源ID", dataIndex: "resourceId", width: 88 },
  { title: "详情", dataIndex: "detail", ellipsis: true },
];

const FETCH_LIMIT = 200;

const tablePagination = computed(() => ({
  pageSize: 15,
  showTotal: (t: number) => `共 ${t} 条`,
}));

async function load() {
  loading.value = true;
  try {
    const params: Record<string, string | number> = { limit: FETCH_LIMIT };
    const q = keyword.value.trim();
    if (q) {
      params.keyword = q;
    }
    const { data } = await client.get<LogRow[]>("/admin/audit", { params });
    rows.value = data;
  } catch {
    message.error("加载失败");
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  if (role.value !== "ADMIN") {
    router.replace("/");
    return;
  }
  load();
});
</script>

<style scoped>
.audit-toolbar {
  margin-bottom: 16px;
}

.audit-search {
  width: 100%;
  max-width: 360px;
}

.audit-cell-text {
  cursor: default;
}
</style>
