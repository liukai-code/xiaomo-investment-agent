package com.itlk.myclaudecode.config;

import com.itlk.myclaudecode.tool.FileListTool;
import com.itlk.myclaudecode.tool.FileReadTool;
import com.itlk.myclaudecode.tool.FileWriteTool;
import com.itlk.myclaudecode.tool.FinancialDataTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {

    @Bean
    public FileReadTool fileReadTool() {
        return new FileReadTool();
    }

    @Bean
    public FileWriteTool fileWriteTool() {
        return new FileWriteTool();
    }

    @Bean
    public FileListTool fileListTool() {
        return new FileListTool();
    }

    @Bean
    public FinancialDataTool financialDataTool() {
        return new FinancialDataTool();
    }
}
