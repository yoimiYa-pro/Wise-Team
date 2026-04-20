<template>
  <a-layout class="app-root-layout">
    <a-layout-sider
      class="app-sider"
      breakpoint="lg"
      :collapsed-width="64"
    >
      <div class="logo">任务管理与绩效评估系统</div>
      <a-menu
        v-model:selectedKeys="selectedKeys"
        theme="dark"
        mode="inline"
        @click="onMenuClick"
      >
        <a-menu-item v-for="item in menuItems" :key="item.key">
          <template #icon>
            <component :is="item.icon" />
          </template>
          {{ item.label }}
        </a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout class="app-main">
      <a-layout-header class="header">
        <a-space size="middle">
          <router-link to="/messages" class="msg-bell">
            <a-badge :count="msgUnread" :overflow-count="99">
              <BellOutlined
                style="font-size: 20px; color: rgba(0, 0, 0, 0.65)"
              />
            </a-badge>
          </router-link>
          <span class="user-meta">{{ username }}（{{ roleDisplay }}）</span>
          <a href="#" @click.prevent="logout">退出</a>
        </a-space>
      </a-layout-header>
      <a-layout-content class="content app-content-scroll">
        <router-view />
      </a-layout-content>
    </a-layout>

    <a-modal
      v-model:open="teamPickOpen"
      :title="teamPickTitle"
      ok-text="进入"
      cancel-text="取消"
      @ok="confirmTeamPick"
    >
      <a-select
        v-model:value="pickTeamId"
        show-search
        allow-clear
        placeholder="按团队名称搜索"
        :options="teamPickOptions"
        :filter-option="filterTeamOption"
        style="width: 100%"
      />
      <p v-if="!teamPickOptions.length && teamPickLoaded" class="pick-hint">
        暂无可用团队，请先在「团队管理」中创建或申请加入。
      </p>
    </a-modal>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "ant-design-vue";
import client from "../api/client";
import {
  AuditOutlined,
  BellOutlined,
  CheckSquareOutlined,
  DashboardOutlined,
  LineChartOutlined,
  ProjectOutlined,
  SettingOutlined,
  TagsOutlined,
  TeamOutlined,
  TrophyOutlined,
  UserOutlined,
} from "@ant-design/icons-vue";

const router = useRouter();
const route = useRoute();

const roleDisplay = computed(() => localStorage.getItem("role") || "MEMBER");
const username = computed(() => localStorage.getItem("username") || "");

const menuItems = computed(() => {
  const r = roleDisplay.value;
  const items: { key: string; label: string; icon: object }[] = [];
  if (r === "ADMIN") {
    items.push(
      { key: "/admin/users", label: "用户管理", icon: UserOutlined },
      { key: "/admin/audit", label: "审计日志", icon: AuditOutlined },
      { key: "/admin/system", label: "系统参数", icon: SettingOutlined },
    );
  }
  if (r === "MANAGER" || r === "ADMIN") {
    items.push(
      { key: "/dashboard", label: "管理者看板", icon: DashboardOutlined },
      { key: "/teams", label: "团队管理", icon: TeamOutlined },
      { key: "/tasks-link", label: "团队任务", icon: ProjectOutlined },
      { key: "/ahp-link", label: "AHP 权重", icon: LineChartOutlined },
      { key: "/perf-link", label: "绩效周期", icon: LineChartOutlined },
      { key: "/admin/skill-options", label: "技能可选项", icon: TagsOutlined },
    );
  }
  if (r === "MEMBER") {
    items.push({ key: "/teams", label: "我的团队", icon: TeamOutlined });
  }
  if (r === "MEMBER" || r === "MANAGER") {
    items.push({ key: "/my-profile", label: "个人信息", icon: UserOutlined });
  }
  if (r === "MEMBER" || r === "MANAGER" || r === "ADMIN") {
    items.push({ key: "/messages", label: "消息中心", icon: BellOutlined });
    items.push({
      key: "/my-performance",
      label: "我的绩效",
      icon: TrophyOutlined,
    });
    items.push({
      key: "/my-tasks",
      label: "我的任务",
      icon: CheckSquareOutlined,
    });
  }
  return items;
});

const selectedKeys = ref<string[]>(["/dashboard"]);

type PickKind = "tasks" | "ahp" | "performance";
const teamPickOpen = ref(false);
const pickKind = ref<PickKind | null>(null);
const pickTeamId = ref<number | undefined>(undefined);
const teamPickOptions = ref<{ value: number; label: string }[]>([]);
const teamPickLoaded = ref(false);

const teamPickTitle = computed(() => {
  const m: Record<PickKind, string> = {
    tasks: "选择团队 — 团队任务",
    ahp: "选择团队 — AHP 权重",
    performance: "选择团队 — 绩效周期",
  };
  return pickKind.value ? m[pickKind.value] : "选择团队";
});

function filterTeamOption(input: string, option: { label?: string }) {
  return (option.label ?? "").toLowerCase().includes(input.toLowerCase());
}

async function loadTeamsForPick() {
  teamPickLoaded.value = false;
  teamPickOptions.value = [];
  const r = roleDisplay.value;
  try {
    if (r === "ADMIN") {
      const { data } =
        await client.get<{ id: number; name: string }[]>("/admin/teams");
      teamPickOptions.value = data.map((t) => ({
        value: t.id,
        label: `${t.name}（#${t.id}）`,
      }));
    } else {
      const { data } =
        await client.get<{ id: number; name: string }[]>("/teams/managed");
      teamPickOptions.value = data.map((t) => ({
        value: t.id,
        label: `${t.name}（#${t.id}）`,
      }));
    }
  } catch {
    message.error("加载团队列表失败");
  } finally {
    teamPickLoaded.value = true;
    if (teamPickOptions.value.length === 1) {
      pickTeamId.value = teamPickOptions.value[0].value;
    }
  }
}

function openTeamPick(kind: PickKind) {
  pickKind.value = kind;
  pickTeamId.value = undefined;
  teamPickOpen.value = true;
  loadTeamsForPick();
}

function confirmTeamPick() {
  if (pickTeamId.value == null) {
    message.warning("请选择团队");
    return Promise.reject();
  }
  const id = pickTeamId.value;
  const k = pickKind.value;
  if (k === "tasks") router.push(`/tasks/${id}`);
  else if (k === "ahp") router.push(`/ahp/${id}`);
  else if (k === "performance") router.push(`/performance/${id}`);
  teamPickOpen.value = false;
  pickKind.value = null;
}

const msgUnread = ref(0);
let msgPoll: ReturnType<typeof setInterval> | null = null;

async function fetchUnread() {
  try {
    const { data } = await client.get<{ count: number }>(
      "/messages/unread-count",
    );
    msgUnread.value = data.count ?? 0;
  } catch {
    msgUnread.value = 0;
  }
}

function onMessagesUpdated() {
  fetchUnread();
}

watch(
  () => route.path,
  (p) => {
    if (p.startsWith("/admin")) selectedKeys.value = [p];
    else if (p.startsWith("/tasks/")) selectedKeys.value = ["/tasks-link"];
    else if (p.startsWith("/ahp/")) selectedKeys.value = ["/ahp-link"];
    else if (p.startsWith("/performance/")) selectedKeys.value = ["/perf-link"];
    else selectedKeys.value = [p === "/" ? "/dashboard" : p];
    fetchUnread();
  },
  { immediate: true },
);

onMounted(() => {
  fetchUnread();
  msgPoll = setInterval(fetchUnread, 60000);
  window.addEventListener("messages-updated", onMessagesUpdated);
});

onUnmounted(() => {
  if (msgPoll) clearInterval(msgPoll);
  window.removeEventListener("messages-updated", onMessagesUpdated);
});

function onMenuClick({ key }: { key: string | number }) {
  const k = String(key);
  if (k === "/tasks-link") {
    openTeamPick("tasks");
    return;
  }
  if (k === "/ahp-link") {
    openTeamPick("ahp");
    return;
  }
  if (k === "/perf-link") {
    openTeamPick("performance");
    return;
  }
  router.push(k);
}

function logout() {
  localStorage.clear();
  router.push("/login");
}
</script>

<style scoped>
.app-root-layout {
  height: 100vh;
  overflow: hidden;
}
.app-sider {
  height: 100vh;
  overflow-y: auto;
}
.app-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.logo {
  height: 48px;
  margin: 12px;
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-right: 24px;
  background: #fff;
}
.msg-bell {
  display: inline-flex;
  align-items: center;
}
.user-meta {
  color: rgba(0, 0, 0, 0.45);
}
.content.app-content-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  margin: 16px;
}
.pick-hint {
  margin: 12px 0 0;
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}
</style>
