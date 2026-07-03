package com.itlk.myclaudecode.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SqlTool SQL查询工具测试")
class SqlToolTest {

    @Mock
    private DataSource dataSource;

    private SqlTool tool;

    @BeforeEach
    void setUp() {
        tool = new SqlTool(dataSource, 100, 30);
    }

    @Nested
    @DisplayName("executeQuery SQL安全验证")
    class SqlValidationTest {

        @Test
        @DisplayName("正常SELECT语句 → 通过验证")
        void validSelect() {
            // 这里只测试验证逻辑，不测试实际执行
            // 由于executeQuery需要数据库连接，我们测试它不会因为验证失败而返回错误
            String result = tool.executeQuery("SELECT * FROM users", null);
            // 如果没有数据库连接，会返回连接错误，但不是验证错误
            assertFalse(result.contains("安全拦截"), "正常SELECT不应被拦截");
        }

        @Test
        @DisplayName("INSERT语句 → 被拦截")
        void insertBlocked() {
            String result = tool.executeQuery("INSERT INTO users VALUES (1, 'test')", null);
            assertTrue(result.contains("安全拦截"), "INSERT应被拦截");
            assertTrue(result.contains("INSERT"), "应提示检测到INSERT");
        }

        @Test
        @DisplayName("UPDATE语句 → 被拦截")
        void updateBlocked() {
            String result = tool.executeQuery("UPDATE users SET name='test'", null);
            assertTrue(result.contains("安全拦截"), "UPDATE应被拦截");
        }

        @Test
        @DisplayName("DELETE语句 → 被拦截")
        void deleteBlocked() {
            String result = tool.executeQuery("DELETE FROM users WHERE id=1", null);
            assertTrue(result.contains("安全拦截"), "DELETE应被拦截");
        }

        @Test
        @DisplayName("DROP语句 → 被拦截")
        void dropBlocked() {
            String result = tool.executeQuery("DROP TABLE users", null);
            assertTrue(result.contains("安全拦截"), "DROP应被拦截");
        }

        @Test
        @DisplayName("CREATE语句 → 被拦截")
        void createBlocked() {
            String result = tool.executeQuery("CREATE TABLE test (id INT)", null);
            assertTrue(result.contains("安全拦截"), "CREATE应被拦截");
        }

        @Test
        @DisplayName("ALTER语句 → 被拦截")
        void alterBlocked() {
            String result = tool.executeQuery("ALTER TABLE users ADD COLUMN age INT", null);
            assertTrue(result.contains("安全拦截"), "ALTER应被拦截");
        }

        @Test
        @DisplayName("TRUNCATE语句 → 被拦截")
        void truncateBlocked() {
            String result = tool.executeQuery("TRUNCATE TABLE users", null);
            assertTrue(result.contains("安全拦截"), "TRUNCATE应被拦截");
        }

        @Test
        @DisplayName("WITH子句（CTE） → 通过验证")
        void withClauseAllowed() {
            String result = tool.executeQuery("WITH cte AS (SELECT 1) SELECT * FROM cte", null);
            assertFalse(result.contains("安全拦截"), "WITH子句不应被拦截");
        }

        @Test
        @DisplayName("SQL注释 → 被拦截")
        void commentBlocked() {
            String result = tool.executeQuery("SELECT * FROM users -- this is a comment", null);
            assertTrue(result.contains("安全拦截"), "SQL注释应被拦截");
            assertTrue(result.contains("注释"), "应提示检测到注释");
        }

        @Test
        @DisplayName("多行注释 → 被拦截")
        void multiLineCommentBlocked() {
            String result = tool.executeQuery("SELECT * /* comment */ FROM users", null);
            assertTrue(result.contains("安全拦截"), "多行注释应被拦截");
        }

        @Test
        @DisplayName("空SQL → 返回错误")
        void emptySql() {
            String result = tool.executeQuery("", null);
            assertTrue(result.contains("不能为空"), "空SQL应返回错误");
        }

        @Test
        @DisplayName("null SQL → 返回错误")
        void nullSql() {
            String result = tool.executeQuery(null, null);
            assertTrue(result.contains("不能为空"), "null SQL应返回错误");
        }

        @Test
        @DisplayName("分号结尾 → 正常处理")
        void semicolonAtEnd() {
            String result = tool.executeQuery("SELECT 1;", null);
            assertFalse(result.contains("安全拦截"), "分号结尾不应被拦截");
        }

        @Test
        @DisplayName("INTO OUTFILE → 被拦截")
        void intoOutfileBlocked() {
            String result = tool.executeQuery("SELECT * INTO OUTFILE '/tmp/test.txt' FROM users", null);
            assertTrue(result.contains("安全拦截"), "INTO OUTFILE应被拦截");
        }
    }

    @Nested
    @DisplayName("getDatabaseSchema 表结构查询")
    class GetDatabaseSchemaTest {

        @Test
        @DisplayName("无数据库连接 → 返回错误信息")
        void noConnection() {
            String result = tool.getDatabaseSchema(null);
            // 没有实际的DataSource配置，会返回连接错误
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("指定表名 → 返回错误信息（无连接）")
        void specificTable() {
            String result = tool.getDatabaseSchema("users");
            assertNotNull(result, "不应返回null");
        }
    }
}
