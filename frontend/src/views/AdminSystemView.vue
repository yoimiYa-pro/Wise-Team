<template>
  <div v-if="role !== 'ADMIN'"></div>
  <a-card v-else title="系统参数（平滑系数与 FCE 权重）">
    <a-form :model="cfg" layout="vertical" @finish="onSave">
      <a-form-item
        v-for="k in keys"
        :key="k"
        :name="k"
        :extra="paramHelp(k) || undefined"
      >
        <template #label>
          <div class="param-label">
            <div class="param-title">{{ paramLabel(k) }}</div>
            <div class="param-key-line">{{ k }}</div>
          </div>
        </template>
        <a-input v-model:value="cfg[k]" :placeholder="paramPlaceholder(k)" />
      </a-form-item>
      <a-button type="primary" html-type="submit">保存</a-button>
    </a-form>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { message } from "ant-design-vue";
import client from "../api/client";

const router = useRouter();
const role = ref(localStorage.getItem("role") || "");
const cfg = reactive<Record<string, string>>({});

const keys = computed(() => Object.keys(cfg));

/** 与 backend SystemConfigService 默认键一致；未知键仅显示英文键名 */
const PARAM_INFO: Record<string, { title: string; help: string; placeholder: string }> = {
  "load.smoothing.alpha": {
    title: "负荷平滑系数 α",
    help: "对周负荷等序列做指数平滑时，当前观测的权重。越大越重视最近一周、波动更明显；越小曲线越平。常取 0.2～0.6。",
    placeholder: "0～1 之间，如 0.4",
  },
  "fce.weights.system": {
    title: "系统指标权重",
    help: "模糊综合评估（FCE）中，由任务状态、工时、完成度等系统统计汇总的客观得分占比。与团队管理者评价、互评权重之和宜为 1。",
    placeholder: "0～1，如 0.35",
  },
  "fce.weights.manager": {
    title: "团队管理者评价权重",
    help: "模糊综合评价中团队管理者（直属上级）评价在综合得分中的占比。",
    placeholder: "0～1，如 0.4",
  },
  "fce.weights.peer": {
    title: "同事互评权重",
    help: "FCE 中同伴互评（Peer）在综合得分中的占比。",
    placeholder: "0～1，如 0.25",
  },
};

function paramLabel(k: string) {
  return PARAM_INFO[k]?.title ?? k;
}

function paramHelp(k: string) {
  return PARAM_INFO[k]?.help ?? "";
}

function paramPlaceholder(k: string) {
  return PARAM_INFO[k]?.placeholder ?? "配置值";
}

onMounted(async () => {
  if (role.value !== "ADMIN") {
    router.replace("/");
    return;
  }
  const { data } = await client.get<Record<string, string>>("/admin/system/config");
  Object.keys(data).forEach((k) => {
    cfg[k] = data[k];
  });
});

async function onSave() {
  await client.put("/admin/system/config", { ...cfg });
  message.success("已保存");
}
</script>

<style scoped>
.param-label {
  line-height: 1.45;
}
.param-title {
  font-weight: 500;
}
.param-key-line {
  margin-top: 2px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
  font-weight: normal;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
</style>
