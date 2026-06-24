package com.itlk.myclaudecode.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class FileReadTool {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @Tool(description = "读取指定文件的内容。当用户要求查看文件内容、读取配置文件、分析代码文件时调用此工具。")
    public String readFile(
            @ToolParam(description = "要读取的文件路径") String filePath,
            @ToolParam(description = "文件编码，默认为UTF-8", required = false) String encoding) {
        try {
            Path path = validatePath(filePath);
            checkFileSize(path);

            Charset charset = StandardCharsets.UTF_8;
            if (encoding != null && !encoding.isEmpty()) {
                charset = Charset.forName(encoding);
            }

            String content = Files.readString(path, charset);
            return "文件内容:\n" + content;
        } catch (SecurityException e) {
            return "安全错误: " + e.getMessage();
        } catch (IOException e) {
            return "IO错误: " + e.getMessage();
        } catch (Exception e) {
            return "未知错误: " + e.getMessage();
        }
    }

    private Path validatePath(String filePath) {
        Path path = Paths.get(filePath).normalize();

        // 如果是相对路径，转换为绝对路径
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir"), filePath).normalize();
        }

        Path projectRoot = Paths.get(System.getProperty("user.dir")).normalize();

        if (!path.startsWith(projectRoot)) {
            throw new SecurityException("访问被拒绝: 路径在项目目录之外");
        }

        return path;
    }

    private void checkFileSize(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("文件不存在: " + path);
        }

        long size = Files.size(path);
        if (size > MAX_FILE_SIZE) {
            throw new IOException("文件过大: " + size + " 字节 (最大: " + MAX_FILE_SIZE + ")");
        }
    }
}
