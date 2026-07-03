package com.itlk.myclaudecode.tool;

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

@DisplayName("FileListTool 文件列表工具测试")
class FileListToolTest {

    private FileListTool tool;
    private Path testDir;

    @BeforeEach
    void setUp() throws IOException {
        tool = new FileListTool();
        // 在项目目录下创建测试目录
        testDir = Paths.get(System.getProperty("user.dir"), "test_temp_files");
        Files.createDirectories(testDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        // 清理测试文件
        Files.deleteIfExists(testDir.resolve("file1.txt"));
        Files.deleteIfExists(testDir.resolve("file2.txt"));
        Files.deleteIfExists(testDir.resolve("file2.java"));
        Files.deleteIfExists(testDir.resolve("file3.txt"));
        Files.deleteIfExists(testDir.resolve("test.txt"));
        Files.deleteIfExists(testDir.resolve("root.txt"));
        Files.deleteIfExists(testDir.resolve("subdir/nested.txt"));
        Files.deleteIfExists(testDir.resolve("subdir"));
        try {
            Files.deleteIfExists(testDir);
        } catch (IOException e) {
            // 目录可能不为空，忽略
        }
    }

    @Nested
    @DisplayName("listFiles 文件列表")
    class ListFilesTest {

        @Test
        @DisplayName("列出目录内容")
        void listDirectory() throws IOException {
            Files.writeString(testDir.resolve("file1.txt"), "content1");
            Files.writeString(testDir.resolve("file2.txt"), "content2");
            Files.createDirectory(testDir.resolve("subdir"));

            String result = tool.listFiles(testDir.toString(), null, null);
            assertTrue(result.contains("file1.txt"), "应包含file1.txt");
            assertTrue(result.contains("file2.txt"), "应包含file2.txt");
            assertTrue(result.contains("subdir"), "应包含subdir");
        }

        @Test
        @DisplayName("递归列出")
        void listRecursive() throws IOException {
            Path subdir = testDir.resolve("subdir");
            Files.createDirectory(subdir);
            Files.writeString(testDir.resolve("root.txt"), "root");
            Files.writeString(subdir.resolve("nested.txt"), "nested");

            String result = tool.listFiles(testDir.toString(), true, null);
            assertTrue(result.contains("root.txt"), "应包含根目录文件");
            assertTrue(result.contains("nested.txt"), "应包含嵌套文件");
        }

        @Test
        @DisplayName("非递归列出")
        void listNonRecursive() throws IOException {
            Path subdir = testDir.resolve("subdir");
            Files.createDirectory(subdir);
            Files.writeString(testDir.resolve("root.txt"), "root");
            Files.writeString(subdir.resolve("nested.txt"), "nested");

            String result = tool.listFiles(testDir.toString(), false, null);
            assertTrue(result.contains("root.txt"), "应包含根目录文件");
            // 非递归模式下，nested.txt 不应该直接出现（但 subdir 会出现）
        }

        @Test
        @DisplayName("通配符匹配")
        void patternMatching() throws IOException {
            Files.writeString(testDir.resolve("file1.txt"), "content1");
            Files.writeString(testDir.resolve("file2.java"), "content2");
            Files.writeString(testDir.resolve("file3.txt"), "content3");

            String result = tool.listFiles(testDir.toString(), null, "*.txt");
            assertTrue(result.contains("file1.txt"), "应包含file1.txt");
            assertTrue(result.contains("file3.txt"), "应包含file3.txt");
            assertFalse(result.contains("file2.java"), "不应包含file2.java");
        }

        @Test
        @DisplayName("空目录")
        void emptyDirectory() throws IOException {
            // 创建一个空的子目录来测试
            Path emptyDir = testDir.resolve("empty_subdir");
            Files.createDirectories(emptyDir);
            String result = tool.listFiles(emptyDir.toString(), null, null);
            assertTrue(result.contains("目录为空") || result.contains("没有匹配"),
                    "空目录应返回提示");
            Files.deleteIfExists(emptyDir);
        }

        @Test
        @DisplayName("目录不存在")
        void directoryNotFound() {
            String result = tool.listFiles(testDir.resolve("nonexistent").toString(), null, null);
            assertTrue(result.contains("目录不存在"), "不存在的目录应返回错误");
        }

        @Test
        @DisplayName("传入文件路径 → 返回错误")
        void fileInsteadOfDirectory() throws IOException {
            Path file = testDir.resolve("file.txt");
            Files.writeString(file, "content");

            String result = tool.listFiles(file.toString(), null, null);
            assertTrue(result.contains("不是目录"), "文件路径应返回错误");
        }

        @Test
        @DisplayName("文件信息包含类型和大小")
        void fileInfoFormat() throws IOException {
            Files.writeString(testDir.resolve("test.txt"), "hello");

            String result = tool.listFiles(testDir.toString(), null, null);
            assertTrue(result.contains("[文件]"), "应包含文件类型标识");
            assertTrue(result.contains("bytes"), "应包含文件大小");
        }

        @Test
        @DisplayName("目录信息包含类型标识")
        void dirInfoFormat() throws IOException {
            Files.createDirectory(testDir.resolve("subdir"));

            String result = tool.listFiles(testDir.toString(), null, null);
            assertTrue(result.contains("[目录]"), "应包含目录类型标识");
        }
    }

    @Nested
    @DisplayName("路径安全验证")
    class PathValidationTest {

        @Test
        @DisplayName("路径遍历攻击 → 被拦截")
        void pathTraversal() {
            String result = tool.listFiles("../../../etc", null, null);
            assertTrue(result.contains("安全错误"), "路径遍历应被拦截");
        }
    }
}
