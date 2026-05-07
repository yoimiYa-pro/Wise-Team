<template>
  <div class="login-wrap">
    <div class="login-bg" aria-hidden="true" />
    <div class="login-card-shell">
      <a-card :bordered="false" class="login-card">
        <header class="login-header">
          <div class="login-brand" aria-hidden="true">
            <span class="login-brand-mark">WT</span>
          </div>
          <h1 class="login-title">团队任务管理与绩效评估</h1>
          <p class="login-subtitle">登录以管理团队、任务与绩效数据</p>
        </header>

        <a-form
          class="login-form"
          layout="vertical"
          :model="form"
          hide-required-mark
          @finish="onFinish"
        >
          <a-form-item label="用户名" name="username" :rules="[{ required: true, message: '请输入用户名' }]">
            <a-input
              v-model:value="form.username"
              size="large"
              placeholder="例如 admin、manager1、member1"
              autocomplete="username"
            >
              <template #prefix>
                <UserOutlined class="input-affix-icon" />
              </template>
            </a-input>
          </a-form-item>
          <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
            <a-input-password
              v-model:value="form.password"
              size="large"
              placeholder="默认 Admin@123"
              autocomplete="current-password"
            >
              <template #prefix>
                <LockOutlined class="input-affix-icon" />
              </template>
            </a-input-password>
          </a-form-item>

          <div class="login-options">
            <a-checkbox v-model:checked="rememberUsername">记住账号</a-checkbox>
          </div>

          <a-button
            html-type="submit"
            size="large"
            block
            class="login-submit"
            :loading="submitting"
          >
            登录
          </a-button>
        </a-form>
      </a-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "ant-design-vue";
import { LockOutlined, UserOutlined } from "@ant-design/icons-vue";
import client from "../api/client";

const REMEMBER_FLAG = "rememberLoginUsername";
const SAVED_USERNAME = "savedLoginUsername";

const router = useRouter();
const route = useRoute();
const form = reactive({ username: "", password: "" });
const rememberUsername = ref(false);
const submitting = ref(false);

function safeRedirectPath(raw: unknown): string {
  if (typeof raw !== "string" || !raw.startsWith("/") || raw.startsWith("//")) {
    return "/";
  }
  return raw;
}

onMounted(() => {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("refreshToken");
  rememberUsername.value = localStorage.getItem(REMEMBER_FLAG) === "1";
  if (rememberUsername.value) {
    form.username = localStorage.getItem(SAVED_USERNAME) ?? "";
  }
});

async function onFinish() {
  submitting.value = true;
  try {
    const { data } = await client.post("/auth/login", form);
    localStorage.setItem("accessToken", data.accessToken);
    localStorage.setItem("refreshToken", data.refreshToken);
    localStorage.setItem("role", data.role);
    localStorage.setItem("userId", String(data.userId));
    localStorage.setItem("username", data.username);
    if (rememberUsername.value) {
      localStorage.setItem(REMEMBER_FLAG, "1");
      localStorage.setItem(SAVED_USERNAME, form.username);
    } else {
      localStorage.removeItem(REMEMBER_FLAG);
      localStorage.removeItem(SAVED_USERNAME);
    }
    message.success("登录成功");
    await router.push(safeRedirectPath(route.query.redirect));
  } catch {
    message.error("登录失败");
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
/* 参照 Gemini：冷灰浅底 #F0F4F9 / #EDF2FA、主文 #1F1F1F、次文 #444746、白卡片大圆角与轻阴影 */
.login-wrap {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  overflow: hidden;
  background: #f0f4f9;
}

.login-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 100% 70% at 0% 0%, rgba(237, 242, 250, 0.9), transparent 52%),
    radial-gradient(ellipse 80% 60% at 100% 100%, rgba(232, 239, 248, 0.85), transparent 48%),
    linear-gradient(180deg, #f8fafd 0%, #f0f4f9 45%, #edf2fa 100%);
}

.login-bg::after {
  content: "";
  position: absolute;
  inset: 0;
  opacity: 0.35;
  background-image: linear-gradient(rgba(60, 64, 67, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(60, 64, 67, 0.04) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse 75% 75% at 50% 50%, black, transparent);
}

.login-card-shell {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 440px;
}

.login-card {
  border-radius: 28px;
  background: #ffffff;
  box-shadow:
    0 1px 2px rgba(60, 64, 67, 0.06),
    0 2px 6px rgba(60, 64, 67, 0.04),
    0 12px 32px rgba(60, 64, 67, 0.08);
}

.login-card :deep(.ant-card-body) {
  padding: 44px 44px 40px;
}

@media (max-width: 480px) {
  .login-card :deep(.ant-card-body) {
    padding: 36px 28px 32px;
  }
}

.login-header {
  margin-bottom: 32px;
  text-align: center;
}

.login-brand {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.login-brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: #1f1f1f;
  background: #edf2fa;
  border: 1px solid rgba(60, 64, 67, 0.08);
  box-shadow: 0 1px 2px rgba(60, 64, 67, 0.06);
}

.login-title {
  margin: 0 0 10px;
  font-size: 1.35rem;
  font-weight: 600;
  line-height: 1.35;
  color: #1f1f1f;
  letter-spacing: -0.02em;
}

.login-subtitle {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.55;
  color: #444746;
}

.login-form :deep(.ant-form-item) {
  margin-bottom: 22px;
}

.login-form :deep(.ant-form-item-label > label) {
  color: #444746;
  font-weight: 500;
}

.login-form :deep(.ant-input-affix-wrapper),
.login-form :deep(.ant-input) {
  border-radius: 16px;
  background: #fff !important;
  background-color: #fff !important;
  border-color: #dadce0;
  box-shadow: none !important;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background-color 0.2s ease;
}

/* 去掉内层输入框默认灰底 / 主题填充，避免叠出浅蓝灰块 */
.login-form :deep(.ant-input-affix-wrapper .ant-input),
.login-form :deep(.ant-input-affix-wrapper input.ant-input) {
  background: transparent !important;
  background-color: transparent !important;
  box-shadow: none !important;
}

/* 浏览器自动填充时的浅蓝底 */
.login-form :deep(.ant-input-affix-wrapper input:-webkit-autofill),
.login-form :deep(.ant-input-affix-wrapper input:-webkit-autofill:hover),
.login-form :deep(.ant-input-affix-wrapper input:-webkit-autofill:focus) {
  -webkit-text-fill-color: rgba(0, 0, 0, 0.88);
  box-shadow: 0 0 0 1000px #fff inset !important;
  transition: background-color 99999s ease-out;
}

.login-form :deep(.ant-input-affix-wrapper:hover),
.login-form :deep(.ant-input:hover) {
  border-color: #bdc1c6;
  background-color: #fff !important;
}

.login-form :deep(.ant-input-affix-wrapper-focused),
.login-form :deep(.ant-input-affix-wrapper:focus-within),
.login-form :deep(.ant-input:focus) {
  border-color: #1f1f1f;
  box-shadow: 0 0 0 3px rgba(31, 31, 31, 0.08) !important;
  background-color: #fff !important;
}

.input-affix-icon {
  color: #5f6368;
}

.login-options {
  margin: -2px 0 22px;
}

.login-options :deep(.ant-checkbox-wrapper) {
  color: #444746;
}

.login-submit {
  height: 48px;
  font-weight: 500;
  border-radius: 999px;
  color: #fff !important;
  background: #1f1f1f !important;
  border: none !important;
  box-shadow: 0 1px 2px rgba(60, 64, 67, 0.12);
  transition:
    transform 0.15s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

.login-submit:not(:disabled):hover {
  background: #3c4043 !important;
  box-shadow: 0 2px 8px rgba(60, 64, 67, 0.18);
}

.login-submit:not(:disabled):active {
  transform: translateY(1px);
}
</style>
