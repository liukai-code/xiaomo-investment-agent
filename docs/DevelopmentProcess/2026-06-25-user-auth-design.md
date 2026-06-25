# 用户认证与会话隔离设计文档

> **项目名称：** 金融投资导学 Agent
> **改造阶段：** Phase 5 - 用户认证与多用户会话隔离
> **创建日期：** 2026-06-25
> **版本：** v1.0

---

## 一、改造目标

为系统添加用户注册/登录功能，实现每个用户只能访问自己的会话记录，解决此前所有用户共享全部会话的安全问题。

### 1.1 改造范围

| 范围 | 说明 |
|------|------|
| ✅ 用户注册/登录 | 用户名 + 密码（BCrypt 加密） |
| ✅ Token 认证 | 基于 UUID Token 的会话管理，Redis 存储 |
| ✅ 会话隔离 | 每个用户只能查看/操作自己的会话 |
| ✅ 前端登录页 | 终端风格的登录/注册页面 |
| ✅ 拦截器鉴权 | HandlerInterceptor 拦截未认证请求 |
| ❌ 不引入 Spring Security 框架 | 仅使用 spring-security-crypto 做密码加密 |
| ❌ 不实现角色权限 | 暂不区分管理员/普通用户 |

### 1.2 设计原则

- **轻量实现**：不引入 Spring Security 全家桶，使用 HandlerInterceptor + Token 方案
- **向后兼容**：现有 API 路径不变，仅增加认证要求
- **前后端分离认证**：前端 localStorage 存 token，后端 Redis 存 token

---

## 二、技术方案

### 2.1 认证流程

```
┌─────────┐     POST /api/auth/register      ┌─────────────┐
│  前端    │ ─────────────────────────────────→ │ AuthController│
│ register │ ←───────────────────────────────── │  创建用户     │
└─────────┘     {code:1, data:{id,username}}   └─────────────┘

┌─────────┐     POST /api/auth/login          ┌─────────────┐
│  前端    │ ─────────────────────────────────→ │ AuthController│
│  login   │ ←───────────────────────────────── │  验证密码     │
└─────────┘     {code:1, data:{token,userId}}  │  生成Token    │
                                               └──────┬──────┘
                                                      │
                                                      ▼
                                              ┌──────────────┐
                                              │ TokenManager  │
                                              │ Redis 存储     │
                                              │ 72h TTL       │
                                              └──────────────┘

┌─────────┐     GET /agent/*                  ┌──────────────────┐
│  前端    │ ─────────────────────────────────→ │ AuthInterceptor  │
│  请求    │  Header: Bearer <token>           │  验证Token        │
│          │ ←───────────────────────────────── │  注入userId       │
└─────────┘     401 或放行                     └────────┬─────────┘
                                                        │
                                                        ▼
                                               ┌────────────────┐
                                               │ agentLoop       │
                                               │ Controller      │
                                               │ 按userId过滤数据  │
                                               └────────────────┘
```

### 2.2 Token 存储结构（Redis）

| Key | Value | TTL | 用途 |
|-----|-------|-----|------|
| `auth:token:{token}` | `userId` | 72h | 验证 token 对应的用户 |
| `auth:token:user:{userId}` | `token` | 72h | 登录时删除旧 token（单会话） |

### 2.3 密码加密

使用 `spring-security-crypto` 提供的 `BCryptPasswordEncoder`，不引入完整的 Spring Security 框架。BCrypt 哈希长度固定 60 字符，自带盐值。

---

## 三、新增文件

### 3.1 `User.java` — 用户实体

路径：`src/main/java/com/itlk/myclaudecode/agent/Entity/User.java`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 主键，自增 |
| `username` | String | 用户名，唯一，50 字符 |
| `password` | String | BCrypt 哈希 |
| `createdAt` | LocalDateTime | 创建时间 |

对应数据库表：`users`（`user` 是 PostgreSQL 保留字）

### 3.2 `UserRepository.java`

路径：`src/main/java/com/itlk/myclaudecode/agent/repository/UserRepository.java`

- `findByUsername(String username)` — 登录时查询
- `existsByUsername(String username)` — 注册时校验重名

### 3.3 `TokenManager.java`

路径：`src/main/java/com/itlk/myclaudecode/agent/service/TokenManager.java`

| 方法 | 说明 |
|------|------|
| `createToken(userId)` | 生成 UUID token，存入 Redis，删除旧 token |
| `getUserId(token)` | 从 Redis 查询 token 对应的 userId |
| `removeToken(token)` | 退出登录时删除 token 和 userId 映射 |

### 3.4 `AuthController.java`

路径：`src/main/java/com/itlk/myclaudecode/agent/controller/AuthController.java`

**API 接口：**

| 方法 | 路径 | 认证 | 请求体 | 响应 |
|------|------|------|--------|------|
| POST | `/api/auth/register` | 否 | `{username, password}` | `{id, username}` |
| POST | `/api/auth/login` | 否 | `{username, password}` | `{token, userId, username}` |
| POST | `/api/auth/logout` | 是 | 无 | 无 |
| GET | `/api/auth/me` | 是 | 无 | `{id, username}` |

**校验规则：**
- 用户名不能为空
- 密码长度 ≥ 6 位
- 用户名不可重复
- 登录失败统一返回"用户名或密码错误"（不暴露具体原因）

### 3.5 `AuthInterceptor.java`

路径：`src/main/java/com/itlk/myclaudecode/config/AuthInterceptor.java`

- 从 `Authorization: Bearer <token>` 提取 token
- 调用 `TokenManager.getUserId()` 验证
- 验证通过后将 `userId` 和 `token` 写入 request attribute
- 验证失败返回 HTTP 401 + JSON 错误信息

### 3.6 `WebMvcConfig.java`

路径：`src/main/java/com/itlk/myclaudecode/config/WebMvcConfig.java`

拦截器注册配置：

| 路径 | 拦截 | 说明 |
|------|------|------|
| `/**` | ✅ | 默认拦截所有 |
| `/api/auth/**` | ❌ | 登录/注册接口放行 |
| `/`, `/index.html` | ❌ | 静态页面放行 |
| `/favicon.ico`, `/error` | ❌ | 系统资源放行 |

### 3.7 `GlobalExceptionHandler.java`

路径：`src/main/java/com/itlk/myclaudecode/config/GlobalExceptionHandler.java`

捕获 `RuntimeException`，返回 `Result.error(e.getMessage())`，避免 500 错误直接暴露堆栈。

---

## 四、修改文件

### 4.1 `pom.xml`

新增依赖：
```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### 4.2 `application.yml`

新增 Redis 配置：
```yaml
spring:
  data:
    redis:
      host: 47.93.225.140
      port: 6379
      password: "123456"
      timeout: 3000
```

### 4.3 `Conversation.java`

新增字段：
```java
@Column(name = "user_id")
private Long userId;
```

使用 `Long` 而非 `@ManyToOne` 关联，保持简单。`ddl-auto: update` 会自动添加列。设为 nullable 以兼容已有数据。

### 4.4 `ConversationRepository.java`

新增查询方法：
```java
List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);
```

### 4.5 `AgentLoop.java`（接口）

所有方法新增 `Long userId` 作为第一个参数：
- `createConversation(Long userId, String title)`
- `listConversations(Long userId)`
- `getHistory(Long userId, Long conversationId)`
- `chat(Long userId, Long conversationId, String message)`
- `chatStream(Long userId, Long conversationId, String message)`
- `generateTitle(Long userId, Long conversationId)`

### 4.6 `AgentLoopImpl.java`（实现）

关键改动：
- `createConversation`：设置 `conversation.setUserId(userId)`
- `listConversations`：调用 `findByUserIdOrderByUpdatedAtDesc(userId)`
- `getHistory`、`generateTitle`：增加归属校验
- `getOrCreateConversation`：增加 `userId` 参数，已有会话校验归属
- `checkOwnership(conversation, userId)`：新增私有方法，归属不匹配时抛异常

### 4.7 `agentLoopController.java`

所有端点新增 `HttpServletRequest` 参数，通过 `request.getAttribute("userId")` 获取拦截器注入的用户 ID，传递给 service 层。

### 4.8 `index.html`（前端）

**新增内容：**

| 模块 | 说明 |
|------|------|
| 登录页 HTML | `#authPage` 全屏覆盖层，LOGIN/REGISTER 标签切换 |
| 登录页 CSS | 终端风格，复用现有 CSS 变量 |
| `authFetch()` | 封装 fetch，自动注入 `Authorization` header，处理 401 跳转 |
| `handleLogin()` | 登录逻辑，成功后存 token 到 localStorage |
| `handleRegister()` | 注册逻辑，成功后切换到登录标签 |
| `logout()` | 清除 token + 重置会话状态 + 显示登录页 |
| `updateUsernameDisplay()` | 侧边栏显示当前用户名 |

**修改内容：**

| 函数 | 改动 |
|------|------|
| `loadConversations()` | `fetch` → `authFetch` |
| `createConversation()` | `fetch` → `authFetch` |
| `loadHistory()` | `fetch` → `authFetch` |
| `generateTitle()` | `fetch` → `authFetch` |
| `send()` | `fetch` → `authFetch`，增加 `res.ok` 检查 |
| 初始化 IIFE | 合并主题初始化和认证校验，通过 `/api/auth/me` 验证 token |

**侧边栏新增：**
- 退出按钮（⏻），hover 变红
- Logo 区域显示当前用户名

---

## 五、数据流转

### 5.1 注册流程

```
前端表单 → POST /api/auth/register
  → AuthController.register()
    → 校验用户名非空、密码≥6位、用户名唯一
    → BCrypt 加密密码
    → 保存 User 实体
    → 返回 {id, username}
前端收到成功 → 自动切换到登录标签，预填用户名
```

### 5.2 登录流程

```
前端表单 → POST /api/auth/login
  → AuthController.login()
    → UserRepository.findByUsername()
    → BCrypt.matches() 验证密码
    → TokenManager.createToken()
      → 删除旧 token（Redis）
      → 生成 UUID token
      → 存入 Redis（token→userId, userId→token, TTL 72h）
    → 返回 {token, userId, username}
前端存入 localStorage → 隐藏登录页 → 加载会话列表
```

### 5.3 请求鉴权流程

```
前端请求 → authFetch(url)
  → 从 localStorage 读 token
  → 设置 Authorization: Bearer <token> header
  → 发送请求
    → AuthInterceptor.preHandle()
      → 提取 Bearer token
      → TokenManager.getUserId(token) 查 Redis
      → 有效：request.setAttribute("userId", userId) → 放行
      → 无效：返回 401 JSON
  → 前端收到 401 → clearAuth() → showAuthPage()
```

### 5.4 会话隔离流程

```
请求到达 Controller → getUserId(request)
  → 传入 Service 层
    → listConversations(userId) → 只查该用户的会话
    → getHistory(userId, convId) → 校验会话归属
    → chat/chatStream → getOrCreateConversation(userId, convId)
      → 已有会话：校验归属
      → 新会话：自动设置 userId
```

---

## 六、安全设计

| 风险 | 措施 |
|------|------|
| 密码明文存储 | BCrypt 哈希，自带盐值 |
| 暴力破解 | 前端可扩展验证码，后端暂无限流 |
| Token 窃取 | Redis 存储 + 72h TTL，重启不丢失 |
| 越权访问 | 每个接口校验会话归属 |
| 用户名枚举 | 登录失败统一返回"用户名或密码错误" |
| XSS | 前端 `escapeHtml()` 处理用户输入 |

---

## 七、已知限制

| 限制 | 说明 | 后续方案 |
|------|------|----------|
| 单会话模式 | 同一用户只能在一个设备登录 | 可改为多 token 共存 |
| 无角色权限 | 不区分管理员/普通用户 | 可加 role 字段 + 权限注解 |
| 无注册限流 | 可被恶意批量注册 | 可加 Redis 计数器限流 |
| 无 Token 刷新 | token 过期后需重新登录 | 可加 refresh token 机制 |
| 已有会话无 userId | 数据库中历史会话的 user_id 为 null | 需手动迁移或清理 |

---

## 八、测试验证

### 8.1 后端验证（curl）

```bash
# 注册
curl -X POST http://localhost:4545/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"secret123"}'

# 登录
curl -X POST http://localhost:4545/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"secret123"}'

# 使用 token 访问
TOKEN="<返回的token>"
curl http://localhost:4545/agent/conversation/list \
  -H "Authorization: Bearer $TOKEN"

# 未认证访问（应返回 401）
curl http://localhost:4545/agent/conversation/list
```

### 8.2 前端验证

1. 打开 `http://localhost:4545` → 显示登录页
2. 注册新用户 → 自动切换到登录标签
3. 登录 → 进入聊天界面，会话列表为空
4. 创建会话并发送消息 → 正常工作
5. 退出登录 → 回到登录页，会话内容清空
6. 用另一个用户登录 → 看不到前一个用户的会话
7. 刷新页面 → 保持登录状态（token 在 localStorage）
8. 服务器重启后刷新 → token 仍在 Redis 中，无需重新登录

---

## 九、文件清单

### 新增文件（7 个）

| 文件 | 说明 |
|------|------|
| `agent/Entity/User.java` | 用户实体 |
| `agent/repository/UserRepository.java` | 用户数据访问 |
| `agent/service/TokenManager.java` | Redis Token 管理 |
| `agent/controller/AuthController.java` | 认证接口 |
| `config/AuthInterceptor.java` | 认证拦截器 |
| `config/WebMvcConfig.java` | MVC 配置 |
| `config/GlobalExceptionHandler.java` | 全局异常处理 |

### 修改文件（7 个）

| 文件 | 改动 |
|------|------|
| `pom.xml` | 加 spring-security-crypto、spring-boot-starter-data-redis |
| `application.yml` | 加 Redis 连接配置 |
| `agent/Entity/Conversation.java` | 加 userId 字段 |
| `agent/repository/ConversationRepository.java` | 加按用户查询方法 |
| `agent/service/AgentLoop.java` | 接口方法加 userId 参数 |
| `agent/service/Impl/AgentLoopImpl.java` | 实现用户隔离逻辑 |
| `agent/controller/agentLoopController.java` | 提取 userId 传给 service |
| `static/index.html` | 登录页、authFetch、退出按钮 |
