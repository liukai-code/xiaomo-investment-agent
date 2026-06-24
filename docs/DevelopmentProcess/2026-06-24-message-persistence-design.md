# 消息持久化设计文档

> **项目名称：** 金融投资导学 Agent
> **功能模块：** 消息持久化（PostgreSQL）
> **创建日期：** 2026-06-24
> **版本：** v1.0

---

## 一、需求概述

### 1.1 功能目标

将 Agent 的对话历史从内存存储迁移到 PostgreSQL 数据库，实现消息持久化和多会话隔离。

### 1.2 问题背景

原有的 `AgentLoopImpl` 使用 JVM 内存 `ArrayList<Message>` 存储对话历史，存在以下问题：

| 问题 | 影响 |
|------|------|
| 应用重启丢失所有对话 | 用户体验差，无法延续上下文 |
| 所有用户共享同一个列表 | 多用户场景下消息混乱 |
| 无会话管理 | 无法区分不同对话主题 |
| 内存持续增长 | 长期运行可能导致 OOM |

### 1.3 本期实现范围

| 范围 | 说明 |
|------|------|
| ✅ PostgreSQL 持久化 | 使用 Spring Data JPA + PostgreSQL |
| ✅ 多会话隔离 | 每个会话独立的消息历史 |
| ✅ 会话管理 API | 创建、列表、查看历史 |
| ✅ 自动建表 | JPA `ddl-auto: update` |
| ❌ 不实现用户认证 | 暂无用户系统 |
| ❌ 不实现消息分页 | 暂时返回全部历史 |
| ❌ 不实现上下文窗口裁剪 | 暂取最近 50 条 |

---

## 二、技术方案

### 2.1 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| ORM 框架 | Spring Data JPA | 3.5.0 |
| 数据库 | PostgreSQL | 16.3 |
| 数据库驱动 | postgresql | 42.7.5 |
| 连接池 | HikariCP | 6.3.0 |

### 2.2 数据模型

两张核心表，一对多关系：

```
conversations (会话表)
├── id              BIGSERIAL PRIMARY KEY
├── title           VARCHAR(200)
├── created_at      TIMESTAMP
└── updated_at      TIMESTAMP

chat_messages (消息表)
├── id              BIGSERIAL PRIMARY KEY
├── conversation_id BIGINT → conversations(id)
├── role            VARCHAR(20)  [SYSTEM/USER/ASSISTANT/TOOL]
├── content         TEXT
├── tool_name       VARCHAR(100)
├── tool_call_id    VARCHAR(100)
└── created_at      TIMESTAMP
```

### 2.3 架构变化

**改造前：**
```
Controller → AgentLoopImpl → ArrayList<Message> (内存)
```

**改造后：**
```
Controller → AgentLoopImpl → ConversationRepository (DB)
                            → ChatMessageRepository  (DB)
```

---

## 三、详细设计

### 3.1 目录结构

```
src/main/java/com/itlk/myclaudecode/
├── agent/
│   ├── Entity/
│   │   ├── Conversation.java       # [新增] 会话实体
│   │   ├── ChatMessage.java        # [新增] 消息实体
│   │   ├── MessageRole.java        # [新增] 角色枚举
│   │   └── Result.java             # [已有] 通用响应
│   ├── repository/
│   │   ├── ConversationRepository.java  # [新增] 会话 Repository
│   │   └── ChatMessageRepository.java   # [新增] 消息 Repository
│   ├── controller/
│   │   └── agentLoopController.java     # [修改] 增加会话管理接口
│   └── service/
│       ├── AgentLoop.java               # [修改] 接口增加会话参数
│       └── Impl/
│           └── AgentLoopImpl.java       # [重构] DB 替代内存存储
```

### 3.2 实体类设计

#### 3.2.1 MessageRole 枚举

```java
public enum MessageRole {
    SYSTEM,     // 系统提示词
    USER,       // 用户消息
    ASSISTANT,  // AI 回复
    TOOL        // 工具调用结果
}
```

#### 3.2.2 Conversation 实体

```java
@Data
@Entity
@Table(name = "conversations")
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200)
    private String title;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ChatMessage> messages = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### 3.2.3 ChatMessage 实体

```java
@Data
@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "tool_name", length = 100)
    private String toolName;

    @Column(name = "tool_call_id", length = 100)
    private String toolCallId;

    private LocalDateTime createdAt;
}
```

### 3.3 Repository 设计

#### 3.3.1 ConversationRepository

```java
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findAllByOrderByUpdatedAtDesc();
}
```

#### 3.3.2 ChatMessageRepository

```java
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.id = :convId ORDER BY m.id DESC LIMIT :limit")
    List<ChatMessage> findRecentByConversationId(@Param("convId") Long conversationId, @Param("limit") int limit);
}
```

### 3.4 AgentLoopImpl 重构

#### 3.4.1 上下文构建逻辑

```java
private List<Message> buildContext(Long conversationId) {
    List<Message> context = new ArrayList<>();
    context.add(new SystemMessage(systemPrompt));  // 注入系统提示词

    // 从 DB 加载最近 50 条消息
    List<ChatMessage> recentMessages = chatMessageRepository
            .findRecentByConversationId(conversationId, MAX_CONTEXT_MESSAGES);
    Collections.reverse(recentMessages);  // 查询返回倒序，需反转

    for (ChatMessage msg : recentMessages) {
        switch (msg.getRole()) {
            case USER -> context.add(new UserMessage(msg.getContent()));
            case ASSISTANT -> context.add(new AssistantMessage(msg.getContent()));
        }
    }
    return context;
}
```

#### 3.4.2 同步聊天流程

```
1. 获取或创建会话 (getOrCreateConversation)
2. 保存用户消息到 DB
3. 从 DB 构建上下文 (buildContext)
4. 调用 LLM
5. 保存助手回复到 DB
6. 返回响应
```

#### 3.4.3 流式聊天流程

```
1. 获取或创建会话
2. 保存用户消息到 DB
3. 从 DB 构建上下文
4. 流式调用 LLM
5. doOnNext: 拼接响应片段
6. doOnComplete: 保存完整回复到 DB
7. 返回 Flux<String>
```

**注意：** `doOnComplete` 可能在不同线程执行，需要独立事务。

### 3.5 Controller 设计

```java
@RestController
@RequestMapping("/agent")
public class agentLoopController {

    // ========== 会话管理 ==========
    POST  /agent/conversation                 → 创建会话
    GET   /agent/conversation/list            → 会话列表
    GET   /agent/conversation/{id}/messages   → 历史消息

    // ========== 聊天 ==========
    GET   /agent/chat                         → 同步聊天
    GET   /agent/chat/stream                  → 流式聊天
}
```

### 3.6 配置项

```yaml
spring:
  datasource:
    url: jdbc:postgresql://REDACTED_SERVER_IP:5432/postgres
    username: postgres
    password: 123456
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update    # 开发阶段自动建表，生产环境改用 Flyway
    show-sql: false
```

---

## 四、API 接口文档

### 4.1 创建会话

```
POST /agent/conversation?title=投资入门学习
```

**响应：**
```json
{
  "code": 1,
  "data": {
    "id": 1,
    "title": "投资入门学习",
    "createdAt": "2026-06-24T22:10:00",
    "updatedAt": "2026-06-24T22:10:00"
  }
}
```

### 4.2 会话列表

```
GET /agent/conversation/list
```

**响应：**
```json
{
  "code": 1,
  "data": [
    {"id": 2, "title": "新对话", "updatedAt": "2026-06-24T22:15:00"},
    {"id": 1, "title": "投资入门学习", "updatedAt": "2026-06-24T22:12:00"}
  ]
}
```

### 4.3 历史消息

```
GET /agent/conversation/1/messages
```

**响应：**
```json
{
  "code": 1,
  "data": [
    {"id": 1, "role": "USER", "content": "什么是基金", "createdAt": "..."},
    {"id": 2, "role": "ASSISTANT", "content": "基金是...", "createdAt": "..."}
  ]
}
```

### 4.4 同步聊天

```
GET /agent/chat?conversationId=1&message=什么是基金
```

**响应：**
```json
{
  "code": 1,
  "data": "基金是一种集合投资方式..."
}
```

### 4.5 流式聊天

```
GET /agent/chat/stream?conversationId=1&message=什么是基金
```

**响应：** SSE 流
```
data: 基金
data: 是一种
data: 集合投资
data: 方式
```

---

## 五、测试计划

### 5.1 测试命令

```bash
# 1. 创建会话
curl -X POST "http://localhost:4545/agent/conversation?title=测试对话"

# 2. 同步聊天
curl "http://localhost:4545/agent/chat?conversationId=1&message=你好"

# 3. 流式聊天
curl -N "http://localhost:4545/agent/chat/stream?conversationId=1&message=什么是基金"

# 4. 查看历史
curl "http://localhost:4545/agent/conversation/1/messages"

# 5. 会话列表
curl "http://localhost:4545/agent/conversation/list"

# 6. 持久化验证：重启应用后再次查询历史
curl "http://localhost:4545/agent/conversation/1/messages"
```

### 5.2 验证清单

- [ ] 应用启动，JPA 自动建表成功
- [ ] 创建会话返回正确 ID
- [ ] 同步聊天正常返回回复
- [ ] 流式聊天正常输出
- [ ] 历史消息正确存储
- [ ] 重启应用后历史消息仍在
- [ ] 多个会话之间消息隔离

---

## 六、变更清单

### 6.1 新增文件

| 文件 | 说明 |
|------|------|
| `agent/Entity/Conversation.java` | 会话实体类 |
| `agent/Entity/ChatMessage.java` | 消息实体类 |
| `agent/Entity/MessageRole.java` | 角色枚举 |
| `agent/repository/ConversationRepository.java` | 会话 Repository |
| `agent/repository/ChatMessageRepository.java` | 消息 Repository |

### 6.2 修改文件

| 文件 | 变更 |
|------|------|
| `pom.xml` | 添加 `spring-boot-starter-data-jpa` + `postgresql` 依赖 |
| `application.yml` | 添加数据源和 JPA 配置 |
| `AgentLoop.java` | 接口增加会话管理方法和 conversationId 参数 |
| `AgentLoopImpl.java` | 核心重构：内存 → DB 持久化 |
| `agentLoopController.java` | 增加会话管理端点 |

---

## 七、后续扩展

1. **消息分页**：历史消息增加分页查询
2. **上下文窗口管理**：智能裁剪过长的上下文，保留关键消息
3. **消息搜索**：支持按内容搜索历史消息
4. **用户认证**：接入用户系统，会话绑定用户
5. **Flyway 迁移**：生产环境使用版本化数据库迁移
6. **消息摘要**：老消息压缩为摘要，节省 token

---

## 八、验收标准

- [x] pom.xml 添加 JPA + PostgreSQL 依赖
- [x] application.yml 配置数据源
- [x] 实体类和枚举创建完成
- [x] Repository 接口创建完成
- [x] AgentLoop 接口和实现重构完成
- [x] Controller 会话管理接口完成
- [x] API 文档编写完成
- [ ] 应用启动验证
- [ ] 接口功能测试
- [ ] 持久化验证

---

*文档生成时间：2026-06-24*
*项目仓库：my-claude-code*
