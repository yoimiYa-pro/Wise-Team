<template>
  <div v-if="role !== 'ADMIN'"></div>
  <a-card v-else title="系统参数（平滑系数与 FCE 权重）">
    <a-form :model="cfg" layout="vertical" @finish="onSave">
      <a-form-item v-for="k in keys" :key="k" :name="k" :label="k">
        <a-input v-model:value="cfg[k]" />
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
