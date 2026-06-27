
# SQL 自动生成与执行 Tool 设计文档

> **项目名称：** 金融投资导学 Agent
> **功能模块：** SQL Tool（数据查询与执行）
> **创建日期：** 2026-06-27
> **版本：** v1.0

---

## 一、需求概述

### 1.1 功能目标

为金融投资导学 Agent 增加 SQL 自动生成与执行能力，使 LLM 能够根据用户的自然语言需求，自动编写 SQL 语句并在数据库上执行，返回查询结果。核心场景包括：数据分析查询、报表生成、业务数据探索等。

### 1.2 本期实现范围

| 范围 | 说明 |
|------|------|
| ✅ 自然语言转 SQL | LLM 根据用户意图自动生成 SQL |
| ✅ SELECT 查询执行 | 执行只读查询并返回结构化结果 |
| ✅ 数据库 Schema 感知 | 提供表结构信息供 LLM 生成准确 SQL |
| ✅ 查询结果格式化 | 以表格形式返回结果，便于阅读 |
| ✅ 安全防护 | SQL 注入防护、只读限制、超时控制 |
| ❌ 不支持 DDL/DML | 不允许 CREATE、ALTER、DROP、INSERT、UPDATE、DELETE |
| ❌ 不支持多数据源 | 仅连接应用主数据库 |

### 1.3 设计原则

- **安全性第一**：严格限制为只读 SELECT 操作，防止数据泄露和破坏
- **Schema 驱动**：自动获取表结构，辅助 LLM 生成正确 SQL
- **结果可控**：限制返回行数和执行时间，防止资源耗尽
- **现有模式对齐**：遵循项目现有的 `@Tool` + `@ToolParam` 注解模式

---

## 二、技术方案

### 2.1 实现方式

采用 **Spring AI Function Calling** 机制，创建 `SqlTool` 类，通过 `@Tool` 注解暴露两个方法：

1. **`getDatabaseSchema`** — 获取数据库表结构信息
2. **`executeQuery`** — 执行 SELECT 查询并返回结果

LLM 工作流：用户提问 → LLM 先调用 `getDatabaseSchema` 了解表结构 → 再调用 `executeQuery` 执行 SQL → 将结果整理后回复用户。

### 2.2 核心组件

| 组件 | 职责 |
|------|------|
| `SqlTool` | 提供 schema 查询和 SQL 执行能力 |
| `ToolConfig` | 注册 `SqlTool` Bean |
| `AgentLoopImpl` | 将 `SqlTool` 注入 agent |
| `application.yml` | 配置 SQL 查询相关参数 |

### 2.3 数据流

```
用户: "查询本月新增用户数"
  → LLM 推理，调用 getDatabaseSchema()
  → 返回 users 表结构
  → LLM 生成 SQL: SELECT COUNT(*) FROM users WHERE created_at >= '2026-06-01'
  → LLM 调用 executeQuery(sql)
  → SqlTool 校验 SQL（只读、无注入）→ 执行 → 返回结果
  → LLM 整理结果回复用户: "本月新增用户共 XX 人"
```

---

## 三、详细设计

### 3.1 目录结构

```
src/main/java/com/itlk/myclaudecode/
├── tool/
│   ├── FileListTool.java          # [已有]
│   ├── FileReadTool.java          # [已有]
│   ├── FileWriteTool.java         # [已有]
│   ├── FinancialDataTool.java     # [已有]
│   └── SqlTool.java               # [新增] SQL 查询 Tool
│
└── config/
    └── ToolConfig.java            # [修改] 注册 SqlTool Bean
```

### 3.2 SqlTool 接口设计

#### 3.2.1 getDatabaseSchema

**功能**：获取数据库中所有表的结构信息（表名、列名、列类型、注释）

**方法签名**：
```java
@Tool(description = "获取数据库表结构信息。在执行SQL查询前，应先调用此方法了解表结构，以便生成正确的SQL语句。返回所有表的名称、列名、数据类型和注释。")
public String getDatabaseSchema(
        @ToolParam(description = "可选，指定要查看的表名。不传则返回所有表的结构", required = false) String tableName)
```

**返回格式示例**：
```
=== 数据库表结构 ===

【表: users】用户表
  - id: BIGINT (主键)
  - username: VARCHAR(50) (用户名，唯一)
  - password: VARCHAR(255) (密码，BCrypt加密)
  - created_at: TIMESTAMP (创建时间)

【表: conversations】对话表
  - id: BIGINT (主键)
  - title: VARCHAR(100) (对话标题)
  - user_id: BIGINT (关联users.id)
  - created_at: TIMESTAMP (创建时间)
  - updated_at: TIMESTAMP (更新时间)

【表: chat_messages】消息表
  - id: BIGINT (主键)
  - conversation_id: BIGINT (关联conversations.id)
  - role: VARCHAR(20) (消息角色: USER/ASSISTANT/SYSTEM/TOOL)
  - content: TEXT (消息内容)
  - tool_name: VARCHAR(100) (工具名称)
  - tool_call_id: VARCHAR(100) (工具调用ID)
  - created_at: TIMESTAMP (创建时间)
```

#### 3.2.2 executeQuery

**功能**：执行 SQL SELECT 查询并返回结果

**方法签名**：
```java
@Tool(description = "执行SQL查询语句。仅支持SELECT只读查询，不支持INSERT/UPDATE/DELETE等写操作。执行前请先调用getDatabaseSchema了解表结构。")
public String executeQuery(
        @ToolParam(description = "要执行的SQL SELECT语句，如 SELECT COUNT(*) FROM users") String sql,
        @ToolParam(description = "最大返回行数，默认100，最大1000", required = false) Integer maxRows)
```

**返回格式示例**：
```
=== 查询结果 ===
SQL: SELECT username, created_at FROM users ORDER BY created_at DESC LIMIT 5
返回行数: 5

| username | created_at           |
|----------|----------------------|
| 张三     | 2026-06-27 10:30:00  |
| 李四     | 2026-06-26 15:20:00  |
| 王五     | 2026-06-25 09:10:00  |
| 赵六     | 2026-06-24 18:45:00  |
| 孙七     | 2026-06-23 11:00:00  |
```

**错误返回示例**：
```
=== 查询失败 ===
SQL: DROP TABLE users
错误: 安全拦截 - 仅允许 SELECT 查询，检测到禁止的操作: DROP
```

### 3.3 核心实现逻辑

#### 3.3.1 SqlTool 类结构

```java
@Slf4j
public class SqlTool {

    private final DataSource dataSource;
    private final int defaultMaxRows;
    private final int queryTimeoutSeconds;

    public SqlTool(DataSource dataSource, int defaultMaxRows, int queryTimeoutSeconds) {
        this.dataSource = dataSource;
        this.defaultMaxRows = defaultMaxRows;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    @Tool(description = "获取数据库表结构信息...")
    public String getDatabaseSchema(
            @ToolParam(description = "可选，指定要查看的表名", required = false) String tableName) {
        // 实现见 3.3.2
    }

    @Tool(description = "执行SQL查询语句...")
    public String executeQuery(
            @ToolParam(description = "要执行的SQL SELECT语句") String sql,
            @ToolParam(description = "最大返回行数", required = false) Integer maxRows) {
        // 实现见 3.3.3
    }
}
```

#### 3.3.2 Schema 查询实现

```java
public String getDatabaseSchema(String tableName) {
    try (Connection conn = dataSource.getConnection()) {
        DatabaseMetaData metaData = conn.getMetaData();
        StringBuilder sb = new StringBuilder("=== 数据库表结构 ===\n\n");

        try (ResultSet tables = metaData.getTables(
                conn.getCatalog(), conn.getSchema(), 
                tableName != null ? tableName : "%", 
                new String[]{"TABLE"})) {

            boolean found = false;
            while (tables.next()) {
                found = true;
                String tblName = tables.getString("TABLE_NAME");
                String remarks = tables.getString("REMARKS");

                sb.append("【表: ").append(tblName).append("】");
                if (remarks != null && !remarks.isBlank()) {
                    sb.append(remarks);
                }
                sb.append("\n");

                try (ResultSet columns = metaData.getColumns(
                        conn.getCatalog(), conn.getSchema(), tblName, "%")) {
                    while (columns.next()) {
                        String colName = columns.getString("COLUMN_NAME");
                        String typeName = columns.getString("TYPE_NAME");
                        int colSize = columns.getInt("COLUMN_SIZE");
                        String colRemarks = columns.getString("REMARKS");
                        String nullable = columns.getString("IS_NULLABLE");

                        sb.append("  - ").append(colName)
                          .append(": ").append(typeName)
                          .append("(").append(colSize).append(")");

                        if ("NO".equals(nullable)) {
                            sb.append(" [NOT NULL]");
                        }
                        if (colRemarks != null && !colRemarks.isBlank()) {
                            sb.append(" (").append(colRemarks).append(")");
                        }
                        sb.append("\n");
                    }
                }
                sb.append("\n");
            }

            if (!found) {
                return "未找到" + (tableName != null ? "名为 '" + tableName + "' 的" : "") + "表";
            }
        }
        return sb.toString();
    } catch (Exception e) {
        log.error("[SqlTool] getDatabaseSchema 异常: {}", e.getMessage(), e);
        return "获取表结构失败: " + e.getMessage();
    }
}
```

#### 3.3.3 查询执行实现

```java
public String executeQuery(String sql, Integer maxRows) {
    log.info("[SqlTool] executeQuery 入参: sql={}", sql);

    // 1. SQL 安全校验
    String validationError = validateSql(sql);
    if (validationError != null) {
        log.warn("[SqlTool] SQL安全拦截: {}", validationError);
        return "=== 查询失败 ===\nSQL: " + sql + "\n错误: " + validationError;
    }

    int limit = (maxRows != null && maxRows > 0)
            ? Math.min(maxRows, 1000) : defaultMaxRows;

    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {

        // 2. 设置查询超时
        stmt.setQueryTimeout(queryTimeoutSeconds);

        // 3. 设置只读模式
        conn.setReadOnly(true);

        // 4. 执行查询
        boolean hasResultSet = stmt.execute(sql);

        if (!hasResultSet) {
            return "=== 查询完成 ===\nSQL: " + sql + "\n该语句没有返回结果集";
        }

        try (ResultSet rs = stmt.getResultSet()) {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            // 5. 读取结果（限制行数）
            List<String[]> rows = new ArrayList<>();
            int rowCount = 0;
            while (rs.next() && rowCount < limit) {
                String[] row = new String[columnCount];
                for (int i = 1; i <= columnCount; i++) {
                    Object val = rs.getObject(i);
                    row[i - 1] = val != null ? val.toString() : "NULL";
                }
                rows.add(row);
                rowCount++;
            }

            boolean truncated = rs.next(); // 检查是否还有更多行

            // 6. 格式化为表格
            return formatAsTable(sql, meta, columnCount, rows, truncated);
        }

    } catch (SQLException e) {
        log.error("[SqlTool] executeQuery SQL异常: {}", e.getMessage(), e);
        return "=== 查询失败 ===\nSQL: " + sql + "\n错误: " + e.getMessage();
    } catch (Exception e) {
        log.error("[SqlTool] executeQuery 异常: {}", e.getMessage(), e);
        return "=== 查询失败 ===\nSQL: " + sql + "\n错误: " + e.getMessage();
    }
}
```

#### 3.3.4 SQL 安全校验

```java
private String validateSql(String sql) {
    if (sql == null || sql.isBlank()) {
        return "SQL 语句不能为空";
    }

    // 去除前后空白和末尾分号
    String normalized = sql.strip().replaceAll(";+\\s*$", "").toUpperCase();

    // 1. 只允许 SELECT 和 WITH（CTE）开头
    if (!normalized.startsWith("SELECT") && !normalized.startsWith("WITH")) {
        return "安全拦截 - 仅允许 SELECT 查询，检测到禁止的操作: " +
                (normalized.contains(" ") ? normalized.substring(0, normalized.indexOf(' ')) : normalized);
    }

    // 2. 禁止危险关键字（防止子查询注入 DML）
    String[] forbiddenKeywords = {
        "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE",
        "TRUNCATE", "REPLACE", "MERGE", "GRANT", "REVOKE",
        "EXEC", "EXECUTE", "CALL", "INTO OUTFILE", "INTO DUMPFILE",
        "LOAD_FILE", "COPY", "PG_READ_FILE", "PG_WRITE_FILE"
    };

    for (String keyword : forbiddenKeywords) {
        // 匹配独立单词（前后为空白或标点）
        Pattern pattern = Pattern.compile("\\b" + keyword + "\\b");
        if (pattern.matcher(normalized).find()) {
            return "安全拦截 - 仅允许 SELECT 查询，检测到禁止的关键字: " + keyword;
        }
    }

    // 3. 禁止注释（防止绕过检测）
    if (normalized.contains("--") || normalized.contains("/*")) {
        return "安全拦截 - SQL 中不允许包含注释";
    }

    return null; // 校验通过
}
```

#### 3.3.5 结果格式化

```java
private String formatAsTable(String sql, ResultSetMetaData meta,
                             int columnCount, List<String[]> rows,
                             boolean truncated) throws SQLException {
    StringBuilder sb = new StringBuilder();
    sb.append("=== 查询结果 ===\n");
    sb.append("SQL: ").append(sql).append("\n");
    sb.append("返回行数: ").append(rows.size());
    if (truncated) {
        sb.append("（结果已截断，存在更多数据未显示）");
    }
    sb.append("\n\n");

    if (rows.isEmpty()) {
        sb.append("查询结果为空\n");
        return sb.toString();
    }

    // 获取列名
    String[] headers = new String[columnCount];
    for (int i = 1; i <= columnCount; i++) {
        headers[i - 1] = meta.getColumnLabel(i);
    }

    // 计算每列最大宽度
    int[] widths = new int[columnCount];
    for (int i = 0; i < columnCount; i++) {
        widths[i] = headers[i].length();
    }
    for (String[] row : rows) {
        for (int i = 0; i < columnCount; i++) {
            widths[i] = Math.max(widths[i], Math.min(row[i].length(), 50));
        }
    }

    // 输出表头
    sb.append("| ");
    for (int i = 0; i < columnCount; i++) {
        sb.append(padRight(headers[i], widths[i])).append(" | ");
    }
    sb.append("\n|");
    for (int i = 0; i < columnCount; i++) {
        sb.append("-".repeat(widths[i] + 2)).append("|");
    }
    sb.append("\n");

    // 输出数据行
    for (String[] row : rows) {
        sb.append("| ");
        for (int i = 0; i < columnCount; i++) {
            String val = row[i].length() > 50
                    ? row[i].substring(0, 47) + "..." : row[i];
            sb.append(padRight(val, widths[i])).append(" | ");
        }
        sb.append("\n");
    }

    return sb.toString();
}

private String padRight(String s, int width) {
    if (s.length() >= width) return s;
    return s + " ".repeat(width - s.length());
}
```

### 3.4 ToolConfig 修改

```java
@Configuration
public class ToolConfig {

    // ... 现有 Bean ...

    @Value("${sql-tool.default-max-rows:100}")
    private int sqlDefaultMaxRows;

    @Value("${sql-tool.query-timeout-seconds:30}")
    private int sqlQueryTimeoutSeconds;

    @Bean
    public SqlTool sqlTool(DataSource dataSource) {
        return new SqlTool(dataSource, sqlDefaultMaxRows, sqlQueryTimeoutSeconds);
    }
}
```

### 3.5 AgentLoopImpl 修改

在构造函数中添加 `SqlTool` 参数：

```java
public AgentLoopImpl(ChatModel chatModel,
                     FileReadTool fileReadTool,
                     FileWriteTool fileWriteTool,
                     FileListTool fileListTool,
                     FinancialDataTool financialDataTool,
                     SqlTool sqlTool,                    // [新增]
                     ToolCallbackProvider toolCallbackProvider,
                     @Value("${system-default-prompt}") String systemPrompt) {
    // ...
    ChatClient.Builder builder = ChatClient.builder(chatModel)
            .defaultTools(fileReadTool, fileWriteTool, fileListTool,
                         financialDataTool, sqlTool);     // [新增]
    // ...
}
```

### 3.6 System Prompt 补充

在 `application.yml` 的 `system-default-prompt` 中增加 SQL 工具使用指引：

```yaml
system-default-prompt: |
  # ... 现有内容 ...

  工具使用：
  # ... 现有工具说明 ...

  - 用户询问数据库中的数据、需要统计分析或生成报表时，使用SQL工具：
    1. 先调用 getDatabaseSchema 获取相关表的结构
    2. 根据表结构生成正确的 SELECT 语句
    3. 调用 executeQuery 执行查询
    4. 将查询结果整理成易读的格式回复用户
  - SQL查询仅支持 SELECT 只读操作
  - 查询结果可能有行数限制，如需更多数据可调整 maxRows 参数
```

### 3.7 application.yml 配置

```yaml
# SQL Tool 配置
sql-tool:
  default-max-rows: 100       # 默认最大返回行数
  query-timeout-seconds: 30   # 查询超时时间（秒）
```

---

## 四、安全设计

### 4.1 威胁模型

| 威胁 | 风险等级 | 防护措施 |
|------|----------|----------|
| SQL 注入（LLM 生成恶意 SQL） | 中 | 关键字白名单 + 黑名单双重校验 |
| 数据泄露（查询敏感表） | 中 | 仅 SELECT，不返回密码等敏感字段（后续可加表级白名单） |
| 资源耗尽（大查询/慢查询） | 中 | 行数限制 + 超时控制 + 只读连接 |
| 数据破坏（DDL/DML 执行） | 高 | 严格禁止非 SELECT 语句 |
| 信息探测（通过报错获取 schema） | 低 | 错误信息脱敏 |

### 4.2 防护层级

```
┌─────────────────────────────────────────┐
│  Layer 1: SQL 文本校验                   │
│  - 仅允许 SELECT/WITH 开头              │
│  - 禁止 DDL/DML 关键字                  │
│  - 禁止注释                              │
├─────────────────────────────────────────┤
│  Layer 2: 数据库连接层                   │
│  - Connection.setReadOnly(true)         │
│  - Statement.setQueryTimeout(30s)       │
├─────────────────────────────────────────┤
│  Layer 3: 结果集控制                     │
│  - 最大返回 1000 行                      │
│  - 单元格内容截断（50字符）              │
├─────────────────────────────────────────┤
│  Layer 4: 日志审计                       │
│  - 记录所有执行的 SQL                    │
│  - 记录执行耗时和行数                    │
└─────────────────────────────────────────┘
```

### 4.3 敏感数据保护

当前版本不在 SQL 层面过滤敏感列（如 `users.password`），因为：
1. LLM 的 system prompt 已明确禁止泄露系统信息
2. password 字段为 BCrypt 加密，即使查询也无法获取明文

后续增强方案（可选）：
- 配置表级白名单：`sql-tool.allowed-tables: users, conversations, chat_messages`
- 配置列级黑名单：`sql-tool.blocked-columns: users.password`

---

## 五、测试计划

### 5.1 单元测试

| 测试用例 | 输入 | 预期结果 |
|----------|------|----------|
| SELECT 正常查询 | `SELECT * FROM users LIMIT 5` | 返回格式化表格 |
| 禁止 INSERT | `INSERT INTO users ...` | 返回安全拦截错误 |
| 禁止 DELETE | `DELETE FROM users WHERE id=1` | 返回安全拦截错误 |
| 禁止 DROP | `DROP TABLE users` | 返回安全拦截错误 |
| 禁止注释注入 | `SELECT * /* comment */ FROM users` | 返回安全拦截错误 |
| CTE 查询支持 | `WITH cte AS (...) SELECT ...` | 正常执行 |
| 空 SQL | `""` | 返回 SQL 不能为空错误 |
| 超时查询 | 模拟慢查询 | 30秒后超时返回错误 |
| 行数限制 | 查询超过 100 行 | 返回 100 行 + 截断提示 |
| Schema 查询 | `getDatabaseSchema(null)` | 返回所有表结构 |
| 指定表 Schema | `getDatabaseSchema("users")` | 返回 users 表结构 |
| 不存在的表 | `getDatabaseSchema("not_exist")` | 返回未找到提示 |

### 5.2 集成测试

| 测试场景 | 用户输入 | 预期行为 |
|----------|----------|----------|
| 简单计数 | "现在有多少用户注册了" | LLM 调用 schema → 生成 COUNT SQL → 返回数字 |
| 条件查询 | "今天有哪些新对话" | LLM 查询 conversations 表按日期过滤 |
| 聚合分析 | "每个用户平均多少条消息" | LLM 生成 GROUP BY 查询 |
| 多表关联 | "最近对话的用户是谁" | LLM 生成 JOIN 查询 |
| 拒绝写操作 | "把用户张三的名字改成李四" | LLM 应拒绝或 Tool 返回拦截错误 |

### 5.3 测试命令

```bash
# 启动应用
mvn spring-boot:run

# 测试 SQL 查询（同步）
curl "http://localhost:4545/agent/chat?message=查询数据库中有多少用户"

# 测试 SQL 查询（流式）
curl -N "http://localhost:4545/agent/chat/stream?message=统计每个用户的对话数量"

# 测试 Schema 查询
curl "http://localhost:4545/agent/chat?message=数据库有哪些表"
```

---

## 六、实施步骤

### 6.1 步骤 1：创建 SqlTool 类

**任务**：
1. 在 `tool` 包下创建 `SqlTool.java`
2. 实现 `getDatabaseSchema` 方法
3. 实现 `executeQuery` 方法
4. 实现 SQL 安全校验逻辑
5. 实现结果格式化逻辑

### 6.2 步骤 2：注册与集成

**任务**：
1. 修改 `ToolConfig`，添加 `SqlTool` Bean
2. 修改 `AgentLoopImpl` 构造函数，注入 `SqlTool`
3. 将 `SqlTool` 添加到 `defaultTools(...)` 调用

### 6.3 步骤 3：配置与提示词

**任务**：
1. 在 `application.yml` 添加 `sql-tool` 配置项
2. 在 `system-default-prompt` 中增加 SQL 工具使用指引

### 6.4 步骤 4：测试验证

**任务**：
1. 启动应用，验证 SqlTool 注册成功
2. 测试 Schema 查询功能
3. 测试 SELECT 查询执行
4. 测试安全拦截（DDL/DML 注入）
5. 测试超时和行数限制
6. 端到端测试：通过对话触发 SQL 查询

---

## 七、风险评估

| 风险 | 影响 | 应对方案 |
|------|------|----------|
| LLM 生成错误 SQL | 查询失败 | Schema 感知 + 错误信息反馈给 LLM 自动修正 |
| LLM 被诱导生成恶意 SQL | 数据安全 | 多层安全校验，严格禁止非 SELECT |
| 大查询导致慢响应 | 用户体验 | 超时控制 + 行数限制 |
| 应用数据库无业务数据 | 功能无意义 | 后续可接入独立的分析数据库 |
| password 等敏感字段被查询 | 信息泄露 | BCrypt 加密无法逆向，后续可加列级过滤 |

---

## 八、后续扩展

完成本期 SQL Tool 后，可按以下方向扩展：

1. **表级白名单**：配置允许查询的表，限制数据访问范围
2. **列级脱敏**：对敏感列（手机号、身份证）自动脱敏
3. **查询缓存**：相同 SQL 短时间内缓存结果，减少数据库压力
4. **多数据源**：支持连接外部分析数据库（如 ClickHouse、数据仓库）
5. **可视化图表**：查询结果自动生成图表（配合前端渲染）
6. **SQL 审计日志**：将所有执行的 SQL 持久化到审计表
7. **查询模板**：预置常用查询模板（日报、周报等）

---

## 九、验收标准

- [ ] `SqlTool` 类创建完成，包含 `getDatabaseSchema` 和 `executeQuery` 两个方法
- [ ] `ToolConfig` 注册 `SqlTool` Bean 成功
- [ ] `AgentLoopImpl` 集成 `SqlTool` 完成
- [ ] 应用启动无报错，日志显示 SqlTool 已注册
- [ ] `getDatabaseSchema` 能正确返回所有表结构
- [ ] `getDatabaseSchema("指定表")` 能返回单表结构
- [ ] `executeQuery("SELECT ...")` 能正确执行并返回格式化表格
- [ ] `executeQuery("INSERT ...")` 被安全拦截
- [ ] `executeQuery("DELETE ...")` 被安全拦截
- [ ] `executeQuery("DROP ...")` 被安全拦截
- [ ] 查询超时生效（30秒）
- [ ] 行数限制生效（默认100行）
- [ ] 端到端测试：用户通过自然语言对话可触发 SQL 查询并获得结果
- [ ] System Prompt 更新完成，包含 SQL 工具使用说明

---

*文档生成时间：2026-06-27*
*项目仓库：my-claude-code*
