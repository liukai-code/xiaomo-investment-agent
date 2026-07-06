package com.itlk.myclaudecode.common.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 调试文件日志：将关键流程写入 docs/agent-debug.log，方便排查标的漂移等问题。
 * 生产环境可随时删除此类。
 */
public class DebugFileLogger {

    private static final String LOG_PATH = "docs/agent-debug.log";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public static void log(String tag, String message) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(LOG_PATH, true))) {
            pw.println("[" + LocalDateTime.now().format(FMT) + "] [" + tag + "] " + message);
        } catch (IOException ignored) {
        }
    }

    public static void logResolveStock(String stage, String input, String output) {
        log("RESOLVE", stage + " | input=\"" + input + "\" | output=\"" + output + "\"");
    }

    public static void logGuard(String guardName, String toolName, String detail) {
        log("GUARD", guardName + " | tool=" + toolName + " | " + detail);
    }

    public static void logBuildContext(String stage, String detail) {
        log("CONTEXT", stage + " | " + detail);
    }
}
