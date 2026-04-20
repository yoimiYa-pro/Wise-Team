# 后端代码阅读顺序

按下面顺序读，可以较快建立「配置 → 安全 → 认证 → 异常 → 数据模型 → 业务竖切」的整体图景。路径均相对于本目录 `backend/`。

---

## 第一阶段：环境与入口

1. **`src/main/resources/application.yml`**  
   数据源、端口、Flyway、MyBatis、`app.*`（JWT、CORS、风险模型参数）。后续读代码时随时对照。

2. **`src/main/java/com/teampm/TeamPerformanceApplication.java`**  
   启动类：`@SpringBootApplication`、`@MapperScan`、`@EnableScheduling`。

---

## 第二阶段：安全与认证（请求如何进门、用户是谁）

3. **`src/main/java/com/teampm/security/SecurityConfig.java`**  
   无状态会话、URL 放行规则、`/api/admin/**` 角色、JWT 过滤器在链中的位置。

4. **`src/main/java/com/teampm/security/JwtAuthenticationFilter.java`**  
   从 `Authorization: Bearer` 解析访问令牌并写入 `SecurityContext`。

5. **`src/main/java/com/teampm/security/JwtTokenProvider.java`**  
   签发与校验 JWT，access / refresh 的密钥与 `type` 声明。

6. **`src/main/java/com/teampm/security/UserDetailsServiceImpl.java`**  
   **`src/main/java/com/teampm/security/UserPrincipal.java`**  
   登录与过滤器共用的用户加载方式、角色如何映射为 `ROLE_*`。

7. **`src/main/java/com/teampm/security/SecurityUtils.java`**  
   服务层如何取当前登录用户（`requireUser()`）。

8. **`src/main/java/com/teampm/web/AuthController.java`**  
   **`src/main/java/com/teampm/service/AuthService.java`**  
   登录、刷新、`/me` 的完整第一条业务链。

---

## 第三阶段：横切与配置

9. **`src/main/java/com/teampm/exception/GlobalExceptionHandler.java`**  
   **`src/main/java/com/teampm/exception/ApiException.java`**  
   统一错误响应形态与业务异常约定。

10. **`src/main/java/com/teampm/config/AppProperties.java`**（对照 `application.yml` 的 `app` 段）  
    可调参数入口。

11. **`src/main/java/com/teampm/config/WebConfig.java`**  
    CORS 与前端 Origin 配置来源。

12. **`src/main/java/com/teampm/config/DataInitializer.java`**（可选）  
    本地演示账号与示例团队种子数据。

---

## 第四阶段：数据库与领域模型

13. **`src/main/resources/db/migration/V1__init.sql`**，依次 **`V2__*.sql`、`V3__*.sql`、`V4__*.sql`**  
    表结构、字段含义、业务约束以迁移脚本为准。

14. **`src/main/java/com/teampm/domain/*.java`**  
    与表对应的实体；读时可对照上面迁移脚本。

---

## 第五阶段：一条业务竖切（推荐「任务」线）

15. **`src/main/java/com/teampm/web/TaskController.java`**  
16. **`src/main/java/com/teampm/service/TaskService.java`**  
17. **`src/main/java/com/teampm/mapper/TaskMapper.java`** + **`src/main/resources/mapper/TaskMapper.xml`**  

过程中会自然关联 **`TeamService`**（团队侧权限）、**`AuditService`**、**`InAppMessageService`** 等，按需点开即可。

---

## 第六阶段：按功能扩展阅读

18. **其它控制器** `src/main/java/com/teampm/web/*Controller.java`  
    与任务线同一模式：Controller → 对应 `service` → `mapper` + `resources/mapper/*.xml`。

19. **管理端** `Admin*Controller.java`  
    结合 `SecurityConfig` 中 `hasRole("ADMIN")` 理解权限边界。

---

## 第七阶段：算法与绩效相关

20. **`src/main/java/com/teampm/algo/*.java`**  
    在已理解任务、绩效、团队数据流之后阅读（AHP、风险模型、技能匹配等）。

21. **调用算法的 Service**  
    例如 `AhpService`、`AssignmentService`、`PerformanceService` 等，从接口入口搜 `algo` 包引用即可。

---

## 速查：包职责一览

| 包路径 | 职责 |
|--------|------|
| `com.teampm.web` | REST API |
| `com.teampm.service` | 业务逻辑 |
| `com.teampm.mapper` | MyBatis 接口 |
| `com.teampm.domain` | 实体 |
| `com.teampm.dto` | 对外展示 / 传输对象 |
| `com.teampm.security` | 认证授权与 JWT |
| `com.teampm.config` | Spring 与业务配置 |
| `com.teampm.exception` | 统一异常 |
| `com.teampm.algo` | 评估与推荐相关算法 |

---

读完第一阶段到第五阶段后，即可独立阅读任意模块；第六、七阶段按兴趣或问题驱动补充即可。
