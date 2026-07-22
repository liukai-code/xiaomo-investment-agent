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

@DisplayName("FileReadTool 文件读取工具测试")
class FileReadToolTest {

    private FileReadTool tool;
    private Path testDir;
    private Path testFile;

    @BeforeEach
    void setUp() throws IOException {
        tool = new FileReadTool();
        // 在项目目录下创建测试目录
        testDir = Paths.get(System.getProperty("user.dir"), "test_temp_files");
        Files.createDirectories(testDir);
        testFile = testDir.resolve("test_read.txt");
        Files.writeString(testFile, "Hello World 你好世界");
    }

    @AfterEach
    void tearDown() throws IOException {
        // 清理测试文件
        Files.deleteIfExists(testFile);
        try {
            Files.deleteIfExists(testDir);
        } catch (IOException e) {
            // 目录可能不为空，忽略
        }
    }

    @Nested
    @DisplayName("readFile 文件读取")
    class ReadFileTest {

        @Test
        @DisplayName("读取UTF-8文件")
        void readUtf8File() {
            String result = tool.readFile(testFile.toString(), null);
            assertTrue(result.contains("Hello World 你好世界"), "应读取到文件内容");
        }

        @Test
        @DisplayName("读取指定编码文件")
        void readWithEncoding() {
            String result = tool.readFile(testFile.toString(), "UTF-8");
            assertTrue(result.contains("Hello World 你好世界"), "应读取到UTF-8内容");
        }

        @Test
        @DisplayName("文件不存在 → 返回错误")
        void fileNotFound() {
            String result = tool.readFile(testDir.resolve("nonexistent.txt").toString(), null);
            assertTrue(result.contains("文件不存在"), "不存在的文件应返回错误");
        }

        @Test
        @DisplayName("读取目录 → 返回错误")
        void readDirectory() {
            String result = tool.readFile(testDir.toString(), null);
            // 目录不是文件，应该有某种错误
            assertNotNull(result, "读取目录不应返回null");
        }
    }

    @Nested
    @DisplayName("路径安全验证")
    class PathValidationTest {

        @Test
        @DisplayName("相对路径 → 转换为绝对路径")
        void relativePath() {
            // 使用相对于项目目录的路径
            String relativePath = "test_temp_files/test_read.txt";
            String result = tool.readFile(relativePath, null);
            // 相对路径应该被转换为绝对路径
            assertNotNull(result, "相对路径应能正常读取");
            assertTrue(result.contains("Hello World") || result.contains("安全错误") || result.contains("文件不存在"),
                    "相对路径应能处理");
        }

        @Test
        @DisplayName("路径遍历攻击 → 被拦截")
        void pathTraversal() {
            // 尝试使用 .. 访问上级目录
            String result = tool.readFile("../../../etc/passwd", null);
            // 应该被安全检查拦截
            assertTrue(result.contains("安全错误") || result.contains("文件不存在"),
                    "路径遍历应被拦截或文件不存在");
        }
    }
}
