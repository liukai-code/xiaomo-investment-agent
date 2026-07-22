# 通知系统设计文档

## Context

项目中 ChatView.vue 有一个静态的 Bell 按钮，没有任何功能。需要实现完整的通知系统：管理员发送通知，用户实时接收并查看。

当前项目没有管理员/角色系统，admin.html 使用独立密码登录（无 auth 拦截）。

## 需求确认

- **管理员认证**：独立密码登录，沿用 admin.html 模式
- **通知存储**：PostgreSQL 持久化
- **实时推送**：Redis Pub/Sub + SSE
- **通知范围**：全体广播 + 定向发送给指定用户
- **已读状态**：Redis Set 记录每个用户已读的通知 ID
- **新用户过滤**：新注册用户只看到注册之后的通知，不显示历史通知

## 数据模型

### notifications 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| title | VARCHAR(200) NOT NULL | 通知标题 |
| content | TEXT NOT NULL | 通知正文 |
| created_at | TIMESTAMP | 创建时间（@PrePersist 自动设置） |
| broadcast | BOOLEAN DEFAULT TRUE | 是否为广播通知（true=全体可见，false=仅指定用户可见） |

### notification_recipients 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| notification_id | BIGINT NOT NULL | 通知 ID（外键） |
| user_id | BIGINT NOT NULL | 接收用户 ID |

定向通知（broadcast=false）时，为每个目标用户插入一条记录。广播通知不插入记录。

### 已读状态（Redis）

- Key: `notification:read:{userId}`
- Type: Set
- Value: 已读通知的 ID 集合
- 不设过期（用户活跃期间保留）

### 隐藏状态（Redis）

- Key: `notification:hidden:{userId}`
- Type: Set
- Value: 用户主动隐藏的通知 ID 集合

## 后端架构

### 模块结构

```
notification/
├── entity/
│   ├── Notification.java
│   └── NotificationRecipient.java
├── repository/
│   ├── NotificationRepository.java
│   └── NotificationRecipientRepository.java
├── service/
│   ├── NotificationService.java
│   ├── impl/NotificationServiceImpl.java
│   └── NotificationSseService.java
├── controller/
│   ├── AdminNotificationController.java
│   ├── AdminUserController.java
│   └── NotificationController.java
└── interceptor/
    └── AdminAuthInterceptor.java
```

### 管理员接口 — /api/admin/notifications

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | / | 创建通知（支持 targetUserIds 字段指定接收人） |
| GET | / | 通知列表（分页，按时间倒序） |
| GET | /{id}/recipients | 获取通知的接收人 ID 列表 |
| DELETE | /{id} | 删除通知（级联删除接收人记录） |

管理员认证：application.yml 配置 `admin.password`，admin.html 登录时校验密码，成功后创建 admin token 存 Redis（key: `auth:admin:token:{token}` → `"admin"`，TTL 2h），后续请求带 Bearer token。

### 管理员用户接口 — /api/admin/users

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | / | 获取用户列表（id, email, accountId，不含密码） |

### 用户接口 — /api/notifications

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | / | 获取最近通知列表（广播+定向，过滤注册时间和隐藏） |
| GET | /read-ids | 获取已读通知 ID 集合 |
| GET | /unread-count | 未读数量 |
| POST | /{id}/read | 标记已读 |
| POST | /{id}/hide | 隐藏通知 |
| GET | /stream | SSE 实时推送（按用户过滤） |

### 通知查询逻辑

用户可见的通知 = 广播通知（注册后发送的） + 定向通知（发给该用户的）

```
listRecentForUser(userId, limit):
    registeredAt = userRepository.findById(userId).getCreatedAt()

    // 查询广播通知（createdAt >= 注册时间）+ 定向通知
    if (有定向通知):
        SELECT * FROM notifications
        WHERE (broadcast = true AND created_at >= :registeredAt)
           OR id IN (:targetedNotificationIds)
        ORDER BY created_at DESC
    else:
        SELECT * FROM notifications
        WHERE broadcast = true AND created_at >= :registeredAt
        ORDER BY created_at DESC

    // 过滤隐藏的通知
    过滤掉 notification:hidden:{userId} 中的 ID
```

### 实时推送流程

```
AdminNotificationController.create()
    → NotificationServiceImpl.create(title, content, targetUserIds)
        → 1. 保存通知到 PostgreSQL（设置 broadcast 字段）
        → 2. 定向通知时批量插入 notification_recipients
        → 3. 发布到 Redis Channel "notification:broadcast"（携带 broadcast + targetUserIds）

NotificationSseService
    → 订阅 Redis Channel "notification:broadcast"
    → getNotificationStream(userId) 返回按用户过滤的 Flux
        → 广播通知：推送给所有人
        → 定向通知：只推送给 targetUserIds 中的用户
```

SSE 事件格式：
```
data: {"id":1,"title":"系统维护","content":"...","createdAt":"2026-07-11T10:00:00","broadcast":true}
data: {"id":2,"title":"专属通知","content":"...","createdAt":"...","broadcast":false,"targetUserIds":[100,200]}
```

### Admin Token 管理

- 登录：POST /api/admin/login `{password: "xxx"}` → 返回 `{token: "uuid"}`
- 存储：Redis `auth:admin:token:{token}` → `"admin"`，TTL 2h
- 校验：新建 AdminAuthInterceptor，专门拦截 `/api/admin/**`（除 `/api/admin/login`）
- WebMvcConfig 改动：
  - AuthInterceptor 保持排除 `/api/admin/**`（不拦截管理员路由）
  - 新增 AdminAuthInterceptor 注册，拦截 `/api/admin/**`，排除 `/api/admin/login`
- 现有 `/api/auth/tools`（工具开关）保持不变，不加认证

## 前端设计

### 用户端（Vue SPA）

| 文件 | 说明 |
|------|------|
| `frontend/src/stores/notification.ts` | Pinia store：通知列表、未读数、SSE 连接管理 |
| `frontend/src/api/notification.ts` | API 模块：获取通知、标记已读、SSE 连接 |
| `frontend/src/components/NotificationPanel.vue` | 通知下拉面板组件 |

#### ChatView.vue 改动

1. Bell 按钮加 `@click` 打开/关闭通知面板
2. Bell 按钮加未读数 badge（红色小圆点，>99 显示 99+）
3. 引入 NotificationPanel 组件
4. 页面加载时调用 notificationStore.init() 建立 SSE 连接

#### NotificationPanel 交互

- 点击 Bell 弹出下拉面板，再次点击或点击外部关闭
- 列表展示：标题 + 内容预览（截断） + 相对时间
- 未读通知左侧有蓝色圆点标记
- 点击通知项 → 标记已读（调 API + 更新本地状态）
- 底部"全部标为已读"按钮
- 空状态："暂无通知"

### 管理端（admin.html）

1. 顶部增加标签页切换："工具管理" | "通知管理"
2. 未登录时显示密码输入框（复用现有 admin.html 的暗色风格）
3. 通知管理区域：
   - 发送表单：标题输入 + 正文 textarea + 接收人选择 + 发送按钮
   - 接收人选择：切换"全部用户"或"指定用户"模式
   - 指定用户模式：显示用户列表 checkbox 多选
   - 已发通知列表：标题 + 发送范围标签（全部/定向） + 时间 + 删除按钮
   - 复用现有 toast 提示

## 关键文件路径

### 后端（新建）

- `src/main/java/com/xiaomo/agent/notification/entity/Notification.java`
- `src/main/java/com/xiaomo/agent/notification/entity/NotificationRecipient.java`
- `src/main/java/com/xiaomo/agent/notification/repository/NotificationRepository.java`
- `src/main/java/com/xiaomo/agent/notification/repository/NotificationRecipientRepository.java`
- `src/main/java/com/xiaomo/agent/notification/service/NotificationService.java`
- `src/main/java/com/xiaomo/agent/notification/service/impl/NotificationServiceImpl.java`
- `src/main/java/com/xiaomo/agent/notification/service/NotificationSseService.java`
- `src/main/java/com/xiaomo/agent/notification/controller/AdminNotificationController.java`
- `src/main/java/com/xiaomo/agent/notification/controller/AdminUserController.java`
- `src/main/java/com/xiaomo/agent/notification/controller/NotificationController.java`
- `src/main/java/com/xiaomo/agent/notification/interceptor/AdminAuthInterceptor.java`

### 后端（修改）

- `src/main/java/com/xiaomo/agent/common/config/WebMvcConfig.java` — 注册 AdminAuthInterceptor
- `src/main/resources/application.yml` — 添加 admin.password 配置

### 前端（新建）

- `frontend/src/stores/notification.ts`
- `frontend/src/api/notification.ts`
- `frontend/src/components/NotificationPanel.vue`

### 前端（修改）

- `frontend/src/views/ChatView.vue` — Bell 按钮交互 + 引入 NotificationPanel

### 管理端（修改）

- `src/main/resources/admin/admin.html` — 增加通知管理标签页 + 用户选择器

## 复用的现有代码

- `common/entity/Result.java` — 统一响应体
- `auth/interceptor/AuthInterceptor.java` — 参考其实现 AdminAuthInterceptor
- `auth/service/TokenManager.java` — 参考 Redis token 模式
- `conversation/service/impl/ChatHistoryCacheServiceImpl.java` — 参考 Redis 操作模式
- `frontend/src/api/request.ts` — 复用 Axios 实例
- `frontend/src/stores/auth.ts` — 参考 Composition API store 模式

## 验证方案

1. **后端测试**：为 NotificationService、NotificationController 编写单元测试（28 个用例）
2. **管理员流程**：admin.html 登录 → 发送广播通知 → 发送定向通知 → 查看列表 → 删除通知
3. **用户流程**：ChatView 打开 → Bell 按钮显示未读数 → 点击查看通知 → 标记已读
4. **定向通知**：管理员发送给指定用户 → 只有被选中用户收到 → 其他用户看不到
5. **新用户过滤**：新注册用户只能看到注册之后发送的通知
6. **实时推送**：管理员发送通知时，已打开的 ChatView 页面实时弹出通知
7. **SSE 重连**：断开后前端自动重连
