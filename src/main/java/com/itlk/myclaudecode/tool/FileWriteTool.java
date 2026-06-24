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
import java.nio.file.StandardOpenOption;

@Component
public class FileWriteTool {

    @Tool(description = "写入内容到指定文件。当用户要求创建文件、修改配置文件、保存代码文件时调用此工具。")
    public String writeFile(
            @ToolParam(description = "要写入的文件路径") String filePath,
            @ToolParam(description = "要写入的内容") String content,
            @ToolParam(description = "文件编码，默认为UTF-8", required = false) String encoding,
            @ToolParam(description = "是否追加模式，默认为覆盖模式", required = false) Boolean append) {
        try {
            Path path = validatePath(filePath);

            Charset charset = StandardCharsets.UTF_8;
            if (encoding != null && !encoding.isEmpty()) {
                charset = Charset.forName(encoding);
            }

            // 确保父目录存在
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            if (Boolean.TRUE.equals(append)) {
                Files.writeString(path, content, charset, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                return "内容已追加到文件: " + path;
            } else {
                Files.writeString(path, content, charset, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return "文件已写入: " + path;
            }
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
}
