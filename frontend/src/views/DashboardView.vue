<template>
  <a-space direction="vertical" size="large" style="width: 100%">
    <a-typography-title :level="4" style="margin: 0">管理者看板</a-typography-title>
    <a-select
      v-model:value="teamId"
      style="width: 280px"
      placeholder="选择团队"
      :options="teams.map((t) => ({ label: t.name, value: t.id }))"
    />
    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :lg="12">
        <a-card><div ref="pieEl" style="height: 320px" /></a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card><div ref="barEl" style="height: 320px" /></a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card><div ref="radarEl" style="height: 360px" /></a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card><div ref="lineEl" style="height: 360px" /></a-card>
      </a-col>
    </a-row>
  </a-space>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import * as echarts from "echarts";
import { message } from "ant-design-vue";
import client from "../api/client";

type Team = { id: number; name: string };

const router = useRouter();
const teams = ref<Team[]>([]);
const teamId = ref<number | undefined>();
const dash = ref<Record<string, unknown> | null>(null);

const pieEl = ref<HTMLDivElement | null>(null);
const barEl = ref<HTMLDivElement | null>(null);
const radarEl = ref<HTMLDivElement | null>(null);
const lineEl = ref<HTMLDivElement | null>(null);
let pieChart: echarts.ECharts | null = null;
let barChart: echarts.ECharts | null = null;
let radarChart: echarts.ECharts | null = null;
let lineChart: echarts.ECharts | null = null;

function disposeCharts() {
  pieChart?.dispose();
  barChart?.dispose();
  radarChart?.dispose();
  lineChart?.dispose();
  pieChart = barChart = radarChart = lineChart = null;
}

onMounted(() => {
  const r = localStorage.getItem("role");
  if (r !== "MANAGER" && r !== "ADMIN") {
    router.replace("/my-tasks");
    return;
  }
  const loadTeams = async () => {
    try {
      const req =
        r === "ADMIN"
          ? client.get<Team[]>("/admin/teams")
          : client.get<Team[]>("/teams/managed");
      const { data } = await req;
      teams.value = data;
      if (data.length && teamId.value == null) teamId.value = data[0].id;
    } catch {
      message.error("加载团队失败");
    }
  };
  loadTeams();
});

watch(
  () => teamId.value,
  async (tid) => {
    if (!tid) return;
    try {
      const { data } = await client.get(`/dashboard/teams/${tid}`);
      dash.value = data;
    } catch {
      message.error("加载看板失败");
    }
  }
);

watch(
  () => dash.value,
  async (d) => {
    if (!d) return;
    await nextTick();
    disposeCharts();
    if (!pieEl.value || !barEl.value || !radarEl.value || !lineEl.value) return;
    const pie = (dash.value?.taskStatusPie || {}) as Record<string, number>;
    const pieOpt = {
      title: { text: "任务状态分布", left: "center" },
      tooltip: { trigger: "item" },
      series: [
        {
          type: "pie",
          radius: "60%",
          data: Object.keys(pie).map((k) => ({ name: k, value: pie[k] })),
        },
      ],
    };
    const rows = (dash.value?.memberLoad || []) as {
      username: string;
      displayName?: string;
      loadRatio: number;
    }[];
    const barOpt = {
      title: { text: "成员负载率（剩余工时/额定）", left: "center" },
      tooltip: {},
      xAxis: {
        type: "category",
        data: rows.map((x) => (x.displayName?.trim() ? x.displayName : x.username)),
      },
      yAxis: { type: "value", max: 1.5 },
      series: [{ type: "bar", data: rows.map((x) => x.loadRatio), name: "负载率" }],
    };
    const risks = (dash.value?.riskTasks || []) as { title: string; delayProbability: number }[];
    const indicators = risks.slice(0, 6).map((t) => ({
      name: t.title.length > 8 ? t.title.slice(0, 8) + "…" : t.title,
      max: 1,
    }));
    const vals = risks.slice(0, 6).map((t) => Number(t.delayProbability || 0));
    while (vals.length < 3) {
      indicators.push({ name: "—", max: 1 });
      vals.push(0);
    }
    const radarOpt = {
      title: { text: "风险概率雷达（进行中任务）", left: "center" },
      radar: { indicator: indicators },
      series: [{ type: "radar", data: [{ value: vals, name: "延期概率" }] }],
    };
    pieChart = echarts.init(pieEl.value);
    barChart = echarts.init(barEl.value);
    radarChart = echarts.init(radarEl.value);
    lineChart = echarts.init(lineEl.value);
    pieChart.setOption(pieOpt);
    barChart.setOption(barOpt);
    radarChart.setOption(radarOpt);
    const uid = Number(localStorage.getItem("userId") || 0);
    if (uid) {
      try {
        const { data } = await client.get<{ score: number }[]>(`/performance/users/${uid}/trend`);
        const tr = data || [];
        lineChart.setOption({
          title: { text: "个人绩效得分趋势", left: "center" },
          xAxis: { type: "category", data: tr.map((_x, i) => `R${i + 1}`) },
          yAxis: { type: "value", min: 0, max: 100 },
          series: [{ type: "line", data: tr.map((x) => x.score), smooth: true }],
        });
      } catch {
        lineChart.setOption({
          title: { text: "个人绩效得分趋势", left: "center" },
          xAxis: { type: "category", data: [] },
          yAxis: { type: "value" },
          series: [{ type: "line", data: [] }],
        });
      }
    }
  }
);

onBeforeUnmount(() => disposeCharts());
</script>
