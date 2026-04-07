import { createRouter, createWebHistory } from "vue-router";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      name: "login",
      component: () => import("../views/LoginView.vue"),
      meta: { public: true },
    },
    {
      path: "/",
      component: () => import("../layouts/MainLayout.vue"),
      meta: { requiresAuth: true },
      children: [
        { path: "", redirect: "/dashboard" },
        { path: "dashboard", name: "dashboard", component: () => import("../views/DashboardView.vue") },
        { path: "teams", name: "teams", component: () => import("../views/TeamsView.vue") },
        { path: "tasks/:teamId", name: "tasks", component: () => import("../views/TasksView.vue") },
        { path: "ahp/:teamId", name: "ahp", component: () => import("../views/AhpView.vue") },
        { path: "performance/:teamId", name: "performance", component: () => import("../views/PerformanceView.vue") },
        { path: "my-performance", name: "my-performance", component: () => import("../views/MyPerformanceView.vue") },
        { path: "my-tasks", name: "my-tasks", component: () => import("../views/MemberTasksView.vue") },
        { path: "my-profile", name: "my-profile", component: () => import("../views/MyProfileView.vue") },
        { path: "messages", name: "messages", component: () => import("../views/MessagesView.vue") },
        { path: "admin/users", name: "admin-users", component: () => import("../views/AdminUsersView.vue") },
        { path: "admin/audit", name: "admin-audit", component: () => import("../views/AdminAuditView.vue") },
        { path: "admin/system", name: "admin-system", component: () => import("../views/AdminSystemView.vue") },
        {
          path: "admin/skill-options",
          name: "admin-skill-options",
          component: () => import("../views/SkillOptionsView.vue"),
        },
      ],
    },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
});

router.beforeEach((to, _from, next) => {
  if (to.meta.public) {
    next();
    return;
  }
  if (to.meta.requiresAuth && !localStorage.getItem("accessToken")) {
    next({ name: "login", query: { redirect: to.fullPath } });
    return;
  }
  next();
});

export default router;
