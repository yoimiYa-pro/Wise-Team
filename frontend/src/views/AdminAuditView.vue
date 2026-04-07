<template>
  <div v-if="role !== 'ADMIN'"></div>
  <a-card v-else title="审计日志">
    <a-table
      :row-key="(r: LogRow) => r.id"
      :data-source="rows"
      :columns="columns"
      :pagination="{ pageSize: 15 }"
    >
      <template #bodyCell="{ column, text }">
        <template v-if="column.key === 'action'">
          <a-tag>{{ text }}</a-tag>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import client from "../api/client";

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

const columns = [
  { title: "ID", dataIndex: "id", width: 70 },
  { title: "操作者", dataIndex: "actorId" },
  { title: "动作", dataIndex: "action", key: "action" },
  { title: "资源", dataIndex: "resourceType" },
  { title: "资源ID", dataIndex: "resourceId" },
  { title: "详情", dataIndex: "detail", ellipsis: true },
];

onMounted(() => {
  if (role.value !== "ADMIN") {
    router.replace("/");
    return;
  }
  client.get<LogRow[]>("/admin/audit").then(({ data }) => (rows.value = data));
});
</script>
