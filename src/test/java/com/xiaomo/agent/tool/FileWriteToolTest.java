package com.xiaomo.agent.tool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileWriteTool 文件写入工具测试")
class FileWriteToolTest {

    private FileWriteTool tool;
    private Path testDir;

    @BeforeEach
    void setUp() throws IOException {
        tool = new FileWriteTool();
        // 在项目目录下创建测试目录
        testDir = Paths.get(System.getProperty("user.dir"), "test_temp_files");
        Files.createDirectories(testDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        // 清理测试文件
        Files.deleteIfExists(testDir.resolve("new_file.txt"));
        Files.deleteIfExists(testDir.resolve("existing.txt"));
        Files.deleteIfExists(testDir.resolve("append.txt"));
        Files.deleteIfExists(testDir.resolve("utf8.txt"));
        Files.deleteIfExists(testDir.resolve("subdir/nested/file.txt"));
        Files.deleteIfExists(testDir.resolve("subdir/nested"));
        Files.deleteIfExists(testDir.resolve("subdir"));
        try {
            Files.deleteIfExists(testDir);
        } catch (IOException e) {
            // 目录可能不为空，忽略
        }
    }

    @Nested
    @DisplayName("writeFile 文件写入")
    class WriteFileTest {

        @Test
        @DisplayName("创建新文件")
        void createNewFile() {
            Path file = testDir.resolve("new_file.txt");
            String result = tool.writeFile(file.toString(), "Hello World", null, null);
            assertTrue(result.contains("已写入"), "应返回写入成功");

            try {
                String content = Files.readString(file);
                assertEquals("Hello World", content, "文件内容应匹配");
            } catch (IOException e) {
                fail("读取文件失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("覆盖已有文件")
        void overwriteExistingFile() throws IOException {
            Path file = testDir.resolve("existing.txt");
            Files.writeString(file, "old content");

            String result = tool.writeFile(file.toString(), "new content", null, null);
            assertTrue(result.contains("已写入"), "应返回写入成功");

            String content = Files.readString(file);
            assertEquals("new content", content, "文件内容应被覆盖");
        }

        @Test
        @DisplayName("追加模式")
        void appendMode() throws IOException {
            Path file = testDir.resolve("append.txt");
            Files.writeString(file, "line1\n");

            String result = tool.writeFile(file.toString(), "line2", null, true);
            assertTrue(result.contains("已追加"), "应返回追加成功");

            String content = Files.readString(file);
            assertEquals("line1\nline2", content, "内容应被追加");
        }

        @Test
        @DisplayName("写入UTF-8内容")
        void writeUtf8Content() {
            Path file = testDir.resolve("utf8.txt");
            String result = tool.writeFile(file.toString(), "你好世界", "UTF-8", null);
            assertTrue(result.contains("已写入"), "UTF-8写入应成功");

            try {
                String content = Files.readString(file);
                assertEquals("你好世界", content, "UTF-8内容应正确");
            } catch (IOException e) {
                fail("读取文件失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("自动创建父目录")
        void createParentDirectories() {
            Path file = testDir.resolve("subdir/nested/file.txt");
            String result = tool.writeFile(file.toString(), "nested content", null, null);
            assertTrue(result.contains("已写入"), "应自动创建父目录");

            assertTrue(Files.exists(file), "文件应存在");
        }
    }

    @Nested
    @DisplayName("路径安全验证")
    class PathValidationTest {

        @Test
        @DisplayName("路径遍历攻击 → 被拦截")
        void pathTraversal() {
            String result = tool.writeFile("../../../tmp/hacked.txt", "malicious", null, null);
            assertTrue(result.contains("安全错误"), "路径遍历应被拦截");
        }
    }
}
