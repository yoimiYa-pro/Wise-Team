<template>
  <a-layout class="app-root-layout">
    <a-layout-sider
      v-model:collapsed="siderCollapsed"
      class="app-sider"
      breakpoint="lg"
      :collapsed-width="72"
      :width="220"
      collapsible
    >
      <div class="sider-head">
        <div
          class="logo"
          :class="{ 'logo--collapsed': siderCollapsed }"
          :title="collapsedLogoTitle"
        >
          {{ siderCollapsed ? "WT" : "融合智能决策的任务管理与绩效评估系统" }}
        </div>
        <div class="sider-head-divider" aria-hidden="true" />
      </div>
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
        <div class="header-bg" aria-hidden="true" />
        <div class="header-left">
          <router-link
            to="/"
            class="header-mark-link"
            title="任务管理与绩效评估系统 · 返回首页"
          >
            <span class="header-mark">WT</span>
          </router-link>
          <nav class="header-breadcrumb-wrap" aria-label="面包屑">
            <a-breadcrumb class="header-breadcrumb" separator="/">
              <a-breadcrumb-item
                v-for="(item, idx) in breadcrumbItems"
                :key="`${idx}-${item.title}`"
              >
                <router-link
                  v-if="item.to != null && idx < breadcrumbItems.length - 1"
                  :to="item.to"
                >
                  {{ item.title }}
                </router-link>
                <span v-else>{{ item.title }}</span>
              </a-breadcrumb-item>
            </a-breadcrumb>
          </nav>
        </div>
        <div class="header-right">
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
        </div>
      </a-layout-header>
      <a-layout-content class="content app-content-scroll">
        <h1 class="page-sr-title">{{ pageHeading }}</h1>
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
  CommentOutlined,
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

const SIDER_COLLAPSED_KEY = "layoutSiderCollapsed";
const siderCollapsed = ref(localStorage.getItem(SIDER_COLLAPSED_KEY) === "1");
const collapsedLogoTitle = "任务管理与绩效评估系统";

watch(siderCollapsed, (v) => {
  localStorage.setItem(SIDER_COLLAPSED_KEY, v ? "1" : "0");
});

type BreadcrumbItem = { title: string; to?: string };

/** 顶栏面包屑（与路由一致；团队子页带「团队管理」入口） */
const breadcrumbItems = computed<BreadcrumbItem[]>(() => {
  const p = route.path;
  const name = route.name as string | undefined;
  const items: BreadcrumbItem[] = [{ title: "首页", to: "/" }];

  if (p === "/" || p === "") {
    return items;
  }

  const tid = route.params.teamId as string | undefined;
  if (name === "tasks" && tid != null && tid !== "") {
    items.push({ title: "团队管理", to: "/teams" });
    items.push({ title: `团队任务 · 团队 #${tid}` });
    return items;
  }
  if (name === "ahp" && tid != null && tid !== "") {
    items.push({ title: "团队管理", to: "/teams" });
    items.push({ title: `AHP 权重 · 团队 #${tid}` });
    return items;
  }
  if (name === "performance" && tid != null && tid !== "") {
    const role = localStorage.getItem("role") || "MEMBER";
    if (role === "MEMBER") {
      items.push({ title: "我的团队", to: "/teams" });
      items.push({ title: `同事互评 · 团队 #${tid}` });
      return items;
    }
    items.push({ title: "团队管理", to: "/teams" });
    items.push({ title: `绩效周期 · 团队 #${tid}` });
    return items;
  }

  const flat: Record<string, string> = {
    dashboard: "管理者看板",
    teams: "团队管理",
    "my-performance": "我的绩效",
    "my-tasks": "我的任务",
    "my-profile": "个人信息",
    messages: "消息中心",
    "admin-users": "用户管理",
    "admin-audit": "审计日志",
    "admin-system": "系统参数",
    "admin-skill-options": "技能可选项",
  };
  if (name && flat[name]) {
    items.push({ title: flat[name] });
    return items;
  }

  items.push({ title: "当前页" });
  return items;
});

/** 与面包屑末级一致，供读屏 h1（主内容区不再重复视觉标题） */
const pageHeading = computed(() => {
  const items = breadcrumbItems.value;
  const last = items[items.length - 1];
  return last?.title ?? "当前页";
});

const roleDisplay = computed(() => localStorage.getItem("role") || "MEMBER");
const username = computed(() => localStorage.getItem("username") || "");

/** 成员侧栏「同事互评」：后端判定当前日期落在某开放周期内时才展示 */
const peerReviewEligibleTeams = ref<{ id: number; name: string }[]>([]);

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
    if (peerReviewEligibleTeams.value.length > 0) {
      items.push({
        key: "/peer-review-link",
        label: "同事互评",
        icon: CommentOutlined,
      });
    }
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

type PickKind = "tasks" | "ahp" | "performance" | "peer";
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
    peer: "选择团队 — 同事互评",
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
  const kind = pickKind.value;
  try {
    if (kind === "peer") {
      const { data } = await client.get<{ id: number; name: string }[]>(
        "/performance/peer-review/eligible-teams",
      );
      teamPickOptions.value = (data ?? []).map((t) => ({
        value: t.id,
        label: `${t.name}（#${t.id}）`,
      }));
    } else if (r === "ADMIN") {
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
  else if (k === "performance" || k === "peer")
    router.push(`/performance/${id}`);
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

async function refreshPeerReviewMenu() {
  if (roleDisplay.value !== "MEMBER") {
    peerReviewEligibleTeams.value = [];
    return;
  }
  try {
    const { data } = await client.get<{ id: number; name: string }[]>(
      "/performance/peer-review/eligible-teams",
    );
    peerReviewEligibleTeams.value = data ?? [];
  } catch {
    peerReviewEligibleTeams.value = [];
  }
}

watch(
  () => route.path,
  (p) => {
    if (p.startsWith("/admin")) selectedKeys.value = [p];
    else if (p.startsWith("/tasks/")) selectedKeys.value = ["/tasks-link"];
    else if (p.startsWith("/ahp/")) selectedKeys.value = ["/ahp-link"];
    else if (p.startsWith("/performance/")) {
      const rl = localStorage.getItem("role") || "MEMBER";
      selectedKeys.value =
        rl === "MEMBER" ? ["/peer-review-link"] : ["/perf-link"];
    } else selectedKeys.value = [p === "/" ? "/dashboard" : p];
    fetchUnread();
    refreshPeerReviewMenu();
  },
  { immediate: true },
);

onMounted(() => {
  fetchUnread();
  msgPoll = setInterval(fetchUnread, 60000);
  window.addEventListener("messages-updated", onMessagesUpdated);
  refreshPeerReviewMenu();
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
  if (k === "/peer-review-link") {
    openTeamPick("peer");
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
  /* 侧栏标题区与右侧顶栏同高，底边与顶栏下边框对齐 */
  --layout-header-height: 56px;
  /* 侧栏系统名与面包屑字号统一 */
  --layout-header-text-size: 17px;
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
  background: #f0f2f5;
}
.sider-head {
  flex-shrink: 0;
  height: var(--layout-header-height);
  min-height: var(--layout-header-height);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: stretch;
}
.logo {
  flex: 1 1 auto;
  min-height: 0;
  margin: 0 16px;
  padding: 6px 4px 4px;
  color: #fff;
  font-family: "STKaiti", "KaiTi", "Kaiti SC", "楷体", "SimKai", serif;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  line-height: 1.45;
  font-size: var(--layout-header-text-size);
}
.logo--collapsed {
  font-size: var(--layout-header-text-size);
  letter-spacing: 0.04em;
}

/* 与主内容区左右留白一致（.content 横向 16px）；贴齐 sider-head 底边，与右侧 .header 底边同一水平线 */
.sider-head-divider {
  flex-shrink: 0;
  height: 2px;
  margin: 0 16px;
  margin-top: auto;
  background: rgba(255, 255, 255, 0.16);
  border-radius: 999px;
}
.header {
  position: relative;
  flex-shrink: 0;
  height: var(--layout-header-height);
  min-height: var(--layout-header-height);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  overflow: hidden;
}
.header-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  background-image:
    radial-gradient(
      ellipse 42% 160% at 8% 50%,
      rgba(22, 119, 255, 0.07),
      transparent 58%
    ),
    radial-gradient(
      ellipse 38% 140% at 92% 50%,
      rgba(22, 119, 255, 0.05),
      transparent 55%
    ),
    radial-gradient(circle at 1px 1px, rgba(0, 0, 0, 0.035) 1px, transparent 0);
  background-size:
    100% 100%,
    100% 100%,
    16px 16px;
  opacity: 0.9;
}
.header-left {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}
.header-mark-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: inherit;
  text-decoration: none;
  flex-shrink: 0;
  border-radius: 8px;
  outline: none;
}
.header-mark-link:focus-visible {
  box-shadow:
    0 0 0 2px #fff,
    0 0 0 4px #1677ff;
}
.header-mark-link:hover .header-mark {
  filter: brightness(1.06);
}
.header-breadcrumb-wrap {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
  margin: 0;
  display: flex;
  align-items: center;
}
.header-breadcrumb {
  width: 100%;
  min-width: 0;
  font-family:
    "SimHei", "Heiti SC", "STHeiti", "PingFang SC", "Microsoft YaHei UI",
    "黑体", "Noto Sans CJK SC", sans-serif;
  font-size: var(--layout-header-text-size);
  line-height: 1.45;
}
.header-breadcrumb :deep(.ant-breadcrumb) {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  overflow: hidden;
  font-family: inherit;
  font-size: inherit;
}
.header-breadcrumb :deep(.ant-breadcrumb-link),
.header-breadcrumb :deep(.ant-breadcrumb-separator),
.header-breadcrumb :deep(li span) {
  font-family: inherit;
  font-size: inherit;
}
.header-breadcrumb :deep(.ant-breadcrumb-separator) {
  margin: 0 6px;
  color: rgba(0, 0, 0, 0.25);
}
.header-breadcrumb :deep(.ant-breadcrumb-link),
.header-breadcrumb :deep(span:not(.ant-breadcrumb-separator)) {
  color: rgba(0, 0, 0, 0.65);
}
.header-breadcrumb :deep(.ant-breadcrumb-link:hover) {
  color: #1677ff;
}
.header-breadcrumb :deep(li:last-child .ant-breadcrumb-link),
.header-breadcrumb :deep(li:last-child span) {
  color: rgba(0, 0, 0, 0.88);
  font-weight: 500;
}
.header-breadcrumb :deep(li) {
  flex-shrink: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.header-mark {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #1677ff 0%, #4096ff 100%);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  box-shadow: 0 1px 4px rgba(22, 119, 255, 0.35);
}
.header-right {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  display: flex;
  align-items: center;
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
  min-width: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 8px 16px 16px;
  margin: 0;
  /* 顶部淡蓝光晕 + 工程网格 + 细点阵，填充灰底空白且不抢内容 */
  background-color: #f0f2f5;
  background-image:
    radial-gradient(
      ellipse 120% 75% at 50% -15%,
      rgba(22, 119, 255, 0.09),
      transparent 58%
    ),
    linear-gradient(rgba(0, 0, 0, 0.024) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 0, 0, 0.024) 1px, transparent 1px),
    radial-gradient(
      circle at 50% 1px,
      rgba(0, 0, 0, 0.035) 1px,
      transparent 1.2px
    );
  background-size:
    100% 100%,
    44px 44px,
    44px 44px,
    22px 22px;
  background-repeat: no-repeat, repeat, repeat, repeat;
}

/* 主内容区内卡片：减轻顶栏与标题区之间的空白感 */
.content.app-content-scroll :deep(.ant-card-head) {
  min-height: 48px;
  padding: 0 16px;
}
.content.app-content-scroll :deep(.ant-card-head-title) {
  padding: 10px 0;
  font-size: 16px;
}
.content.app-content-scroll :deep(.ant-card-body) {
  padding: 16px;
}
.content.app-content-scroll :deep(.ant-card-type-inner .ant-card-body) {
  padding: 12px 16px;
}
.pick-hint {
  margin: 12px 0 0;
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}

.page-sr-title {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
