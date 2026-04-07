<template>
  <div class="login-wrap">
    <a-card title="团队任务管理与绩效评估" style="width: 400px">
      <a-form layout="vertical" :model="form" @finish="onFinish">
        <a-form-item label="用户名" name="username" :rules="[{ required: true, message: '请输入用户名' }]">
          <a-input v-model:value="form.username" placeholder="admin / manager1 / member1" />
        </a-form-item>
        <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
          <a-input-password v-model:value="form.password" placeholder="默认 Admin@123" />
        </a-form-item>
        <a-button type="primary" html-type="submit" block>登录</a-button>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive } from "vue";
import { useRouter } from "vue-router";
import { message } from "ant-design-vue";
import client from "../api/client";

const router = useRouter();
const form = reactive({ username: "", password: "" });

onMounted(() => {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("refreshToken");
});

async function onFinish() {
  try {
    const { data } = await client.post("/auth/login", form);
    localStorage.setItem("accessToken", data.accessToken);
    localStorage.setItem("refreshToken", data.refreshToken);
    localStorage.setItem("role", data.role);
    localStorage.setItem("userId", String(data.userId));
    localStorage.setItem("username", data.username);
    message.success("登录成功");
    router.push("/");
  } catch {
    message.error("登录失败");
  }
}
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a1a2e, #16213e);
}
</style>
