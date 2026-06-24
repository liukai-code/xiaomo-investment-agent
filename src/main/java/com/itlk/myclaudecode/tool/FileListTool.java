package com.itlk.myclaudecode.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

@Component
public class FileListTool {

    @Tool(description = "列出指定目录下的文件和子目录。当用户要求查看项目结构、查找特定文件、了解目录内容时调用此工具。")
    public String listFiles(
            @ToolParam(description = "要列出的目录路径") String dirPath,
            @ToolParam(description = "是否递归列出子目录，默认为false", required = false) Boolean recursive,
            @ToolParam(description = "文件名匹配模式，支持*和?通配符", required = false) String pattern) {
        try {
            Path path = validatePath(dirPath);

            if (!Files.exists(path)) {
                return "目录不存在: " + path;
            }

            if (!Files.isDirectory(path)) {
                return "路径不是目录: " + path;
            }

            List<String> files = new ArrayList<>();

            if (Boolean.TRUE.equals(recursive)) {
                Files.walkFileTree(path, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (matchesPattern(file, pattern)) {
                            files.add(formatFileInfo(file, attrs, path));
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (!dir.equals(path)) {
                            if (matchesPattern(dir, pattern)) {
                                files.add(formatFileInfo(dir, attrs, path));
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                Files.list(path).forEach(file -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                        if (matchesPattern(file, pattern)) {
                            files.add(formatFileInfo(file, attrs, path));
                        }
                    } catch (IOException e) {
                        // 忽略无法读取的文件
                    }
                });
            }

            if (files.isEmpty()) {
                return "目录为空或没有匹配的文件";
            }

            return "文件列表:\n" + String.join("\n", files);
        } catch (SecurityException e) {
            return "安全错误: " + e.getMessage();
        } catch (IOException e) {
            return "IO错误: " + e.getMessage();
        } catch (Exception e) {
            return "未知错误: " + e.getMessage();
        }
    }

    private Path validatePath(String dirPath) {
        Path path = Paths.get(dirPath).normalize();

        // 如果是相对路径，转换为绝对路径
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir"), dirPath).normalize();
        }

        Path projectRoot = Paths.get(System.getProperty("user.dir")).normalize();

        if (!path.startsWith(projectRoot)) {
            throw new SecurityException("访问被拒绝: 路径在项目目录之外");
        }

        return path;
    }

    private boolean matchesPattern(Path path, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return true;
        }

        String fileName = path.getFileName().toString();
        return fileName.matches(pattern.replace("*", ".*").replace("?", "."));
    }

    private String formatFileInfo(Path path, BasicFileAttributes attrs, Path basePath) {
        String relativePath = basePath.relativize(path).toString();
        String type = attrs.isDirectory() ? "[目录]" : "[文件]";
        String size = attrs.isDirectory() ? "" : " (" + attrs.size() + " bytes)";
        return type + " " + relativePath + size;
    }
}
