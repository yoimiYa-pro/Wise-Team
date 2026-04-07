<template>
  <a-space direction="vertical" style="width: 100%" size="large">
    <a-typography-title :level="4" style="margin: 0">我的绩效</a-typography-title>
    <a-typography-text type="secondary">
      绩效均分为系统在周期关闭时根据管理者评价、任务达成与同事互评（FCE）综合更新；下方曲线与表格为各周期生成的报告得分。
    </a-typography-text>

    <a-row :gutter="16">
      <a-col :xs="24" :sm="12" :md="8">
        <a-card size="small">
          <a-statistic title="绩效均分（系统累积）" :value="avgPerfDisplay" suffix="分" />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="8">
        <a-card size="small">
          <a-statistic title="已有周期报告条数" :value="reports.length" suffix="条" />
        </a-card>
      </a-col>
    </a-row>

    <a-card title="周期得分趋势">
      <a-spin :spinning="loading">
        <div v-if="!loading && !reports.length" style="padding: 24px 0">
          <a-empty description="暂无绩效报告（需管理者关闭绩效周期后生成）" />
        </div>
        <div v-else-if="reports.length" ref="chartEl" style="height: 360px" />
      </a-spin>
    </a-card>

    <a-card v-if="reports.length" title="历史报告">
      <a-table
        :row-key="(r: ReportRow) => r.id"
        :data-source="reports"
        :columns="columns"
        :pagination="false"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'score'">
            {{ formatScore(record.score) }}
          </template>
        </template>
      </a-table>
    </a-card>
  </a-space>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as echarts from "echarts";
import { message } from "ant-design-vue";
import client from "../api/client";

type ReportRow = {
  id: number;
  cycleId: number;
  userId?: number;
  score?: number;
};

const chartEl = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;

const loading = ref(true);
const avgPerformance = ref<number | null>(null);
const reports = ref<ReportRow[]>([]);

const uid = () => Number(localStorage.getItem("userId") || 0);

const avgPerfDisplay = computed(() =>
  avgPerformance.value != null && !Number.isNaN(avgPerformance.value) ? avgPerformance.value : "—"
);

/** API 按 id 倒序（新在前），图表从左到右按时间旧→新 */
const reportsChrono = computed(() => [...reports.value].reverse());

const columns = [
  { title: "报告 ID", dataIndex: "id", key: "id", width: 100 },
  { title: "绩效周期 ID", dataIndex: "cycleId", key: "cycleId", width: 140 },
  { title: "综合得分", dataIndex: "score", key: "score" },
];

function formatScore(s?: number) {
  return s != null && !Number.isNaN(Number(s)) ? Number(s).toFixed(2) : "—";
}

async function load() {
  loading.value = true;
  const id = uid();
  if (!id) {
    loading.value = false;
    return;
  }
  try {
    const [meRes, trendRes] = await Promise.all([
      client.get<{ avgPerformance?: number }>("/auth/me"),
      client.get<ReportRow[]>(`/performance/users/${id}/trend`),
    ]);
    const ap = meRes.data?.avgPerformance;
    avgPerformance.value = ap != null ? Number(ap) : null;
    reports.value = Array.isArray(trendRes.data) ? trendRes.data : [];
  } catch {
    message.error("加载绩效数据失败");
    avgPerformance.value = null;
    reports.value = [];
  } finally {
    loading.value = false;
  }
}

function renderChart() {
  chart?.dispose();
  chart = null;
  if (!chartEl.value || !reportsChrono.value.length) return;
  chart = echarts.init(chartEl.value);
  const data = reportsChrono.value;
  chart.setOption({
    title: { text: "各周期综合得分", left: "center" },
    tooltip: { trigger: "axis" },
    grid: { left: 48, right: 24, bottom: 48, top: 48 },
    xAxis: {
      type: "category",
      data: data.map((r) => `#${r.cycleId}`),
      name: "周期",
    },
    yAxis: { type: "value", min: 0, max: 100, name: "得分" },
    series: [
      {
        type: "line",
        name: "得分",
        data: data.map((r) => (r.score != null ? Number(r.score) : 0)),
        smooth: true,
        symbolSize: 8,
      },
    ],
  });
}

watch(
  () => reportsChrono.value,
  async () => {
    await nextTick();
    renderChart();
  },
  { deep: true }
);

onMounted(() => {
  load().then(() => nextTick().then(renderChart));
});

onBeforeUnmount(() => {
  chart?.dispose();
  chart = null;
});
</script>
