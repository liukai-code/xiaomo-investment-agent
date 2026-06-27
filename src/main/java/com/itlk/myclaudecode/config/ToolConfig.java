package com.itlk.myclaudecode.config;

import com.itlk.myclaudecode.tool.FileListTool;
import com.itlk.myclaudecode.tool.FileReadTool;
import com.itlk.myclaudecode.tool.FileWriteTool;
import com.itlk.myclaudecode.tool.FinancialDataTool;
import com.itlk.myclaudecode.tool.SqlTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class ToolConfig {

    @Value("${sql-tool.default-max-rows:100}")
    private int sqlDefaultMaxRows;

    @Value("${sql-tool.query-timeout-seconds:30}")
    private int sqlQueryTimeoutSeconds;

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

    @Bean
    public SqlTool sqlTool(DataSource dataSource) {
        return new SqlTool(dataSource, sqlDefaultMaxRows, sqlQueryTimeoutSeconds);
    }
}
