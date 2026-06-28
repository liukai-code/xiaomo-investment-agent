package com.itlk.myclaudecode.tool;

import lombok.extern.slf4j.Slf4j;
import com.itlk.myclaudecode.tool.annotation.ToolBehavior;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class SqlTool {

    private static final int MAX_ALLOWED_ROWS = 1000;

    private final DataSource dataSource;
    private final int defaultMaxRows;
    private final int queryTimeoutSeconds;

    public SqlTool(DataSource dataSource, int defaultMaxRows, int queryTimeoutSeconds) {
        this.dataSource = dataSource;
        this.defaultMaxRows = defaultMaxRows;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    @Tool(description = "获取数据库表结构信息。在执行SQL查询前，应先调用此方法了解表结构，以便生成正确的SQL语句。返回所有表的名称、列名、数据类型和注释。可传入表名查看指定表的结构。")
    public String getDatabaseSchema(
            @ToolParam(description = "可选，指定要查看的表名。不传则返回所有表的结构", required = false) String tableName) {
        log.info("[SqlTool] getDatabaseSchema 入参: tableName={}", tableName);
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

            String result = sb.toString();
            log.info("[SqlTool] getDatabaseSchema 出参长度: {}", result.length());
            return result;
        } catch (Exception e) {
            log.error("[SqlTool] getDatabaseSchema 异常: {}", e.getMessage(), e);
            return "获取表结构失败: " + e.getMessage();
        }
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = "执行SQL查询语句。仅支持SELECT只读查询，不支持INSERT/UPDATE/DELETE等写操作。执行前请先调用getDatabaseSchema了解表结构。")
    public String executeQuery(
            @ToolParam(description = "要执行的SQL SELECT语句，如 SELECT COUNT(*) FROM users") String sql,
            @ToolParam(description = "最大返回行数，默认100，最大1000", required = false) Integer maxRows) {
        log.info("[SqlTool] executeQuery 入参: sql={}, maxRows={}", sql, maxRows);

        String validationError = validateSql(sql);
        if (validationError != null) {
            log.warn("[SqlTool] SQL安全拦截: {}", validationError);
            return "=== 查询失败 ===\nSQL: " + sql + "\n错误: " + validationError;
        }

        int limit = (maxRows != null && maxRows > 0)
                ? Math.min(maxRows, MAX_ALLOWED_ROWS) : defaultMaxRows;

        try (Connection conn = dataSource.getConnection()) {
            conn.setReadOnly(true);

            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(queryTimeoutSeconds);

                boolean hasResultSet = stmt.execute(sql);

                if (!hasResultSet) {
                    return "=== 查询完成 ===\nSQL: " + sql + "\n该语句没有返回结果集";
                }

                try (ResultSet rs = stmt.getResultSet()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();

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

                    boolean truncated = rs.next();

                    String result = formatAsTable(sql, meta, columnCount, rows, truncated);
                    log.info("[SqlTool] executeQuery 出参长度: {}", result.length());
                    return result;
                }
            }
        } catch (SQLException e) {
            log.error("[SqlTool] executeQuery SQL异常: {}", e.getMessage(), e);
            return "=== 查询失败 ===\nSQL: " + sql + "\n错误: " + e.getMessage();
        } catch (Exception e) {
            log.error("[SqlTool] executeQuery 异常: {}", e.getMessage(), e);
            return "=== 查询失败 ===\nSQL: " + sql + "\n错误: " + e.getMessage();
        }
    }

    private String validateSql(String sql) {
        if (sql == null || sql.isBlank()) {
            return "SQL 语句不能为空";
        }

        String normalized = sql.strip().replaceAll(";+\\s*$", "").toUpperCase();

        if (!normalized.startsWith("SELECT") && !normalized.startsWith("WITH")) {
            String firstToken = normalized.contains(" ")
                    ? normalized.substring(0, normalized.indexOf(' ')) : normalized;
            return "安全拦截 - 仅允许 SELECT 查询，检测到禁止的操作: " + firstToken;
        }

        String[] forbiddenKeywords = {
                "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE",
                "TRUNCATE", "REPLACE", "MERGE", "GRANT", "REVOKE",
                "EXEC", "EXECUTE", "CALL", "INTO OUTFILE", "INTO DUMPFILE",
                "LOAD_FILE", "COPY", "PG_READ_FILE", "PG_WRITE_FILE"
        };

        for (String keyword : forbiddenKeywords) {
            Pattern pattern = Pattern.compile("\\b" + keyword + "\\b");
            if (pattern.matcher(normalized).find()) {
                return "安全拦截 - 仅允许 SELECT 查询，检测到禁止的关键字: " + keyword;
            }
        }

        if (normalized.contains("--") || normalized.contains("/*")) {
            return "安全拦截 - SQL 中不允许包含注释";
        }

        return null;
    }

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

        String[] headers = new String[columnCount];
        for (int i = 1; i <= columnCount; i++) {
            headers[i - 1] = meta.getColumnLabel(i);
        }

        int[] widths = new int[columnCount];
        for (int i = 0; i < columnCount; i++) {
            widths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < columnCount; i++) {
                widths[i] = Math.max(widths[i], Math.min(row[i].length(), 50));
            }
        }

        sb.append("| ");
        for (int i = 0; i < columnCount; i++) {
            sb.append(padRight(headers[i], widths[i])).append(" | ");
        }
        sb.append("\n|");
        for (int i = 0; i < columnCount; i++) {
            sb.append("-".repeat(widths[i] + 2)).append("|");
        }
        sb.append("\n");

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
}
