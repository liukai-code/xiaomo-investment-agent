package com.itlk.myclaudecode.tool.config;

import com.itlk.myclaudecode.tool.FileListTool;
import com.itlk.myclaudecode.tool.FileReadTool;
import com.itlk.myclaudecode.tool.FileWriteTool;
import com.itlk.myclaudecode.tool.FinancialCalcRouterTool;
import com.itlk.myclaudecode.tool.FinancialCalcTool;
import com.itlk.myclaudecode.tool.FinancialDataRouterTool;
import com.itlk.myclaudecode.tool.FinancialDataTool;
import com.itlk.myclaudecode.tool.SqlTool;
import com.itlk.myclaudecode.tool.WebFetchTool;
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

    @Value("${webfetch.proxy.host:}")
    private String webfetchProxyHost;

    @Value("${webfetch.proxy.port:0}")
    private int webfetchProxyPort;

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
    public FinancialCalcTool financialCalcTool() {
        return new FinancialCalcTool();
    }

    @Bean
    public FinancialDataTool financialDataTool() {
        return new FinancialDataTool();
    }

    @Bean
    public FinancialCalcRouterTool financialCalcRouterTool(FinancialCalcTool financialCalcTool) {
        return new FinancialCalcRouterTool(financialCalcTool);
    }

    @Bean
    public FinancialDataRouterTool financialDataRouterTool(FinancialDataTool financialDataTool) {
        return new FinancialDataRouterTool(financialDataTool);
    }

    @Bean
    public SqlTool sqlTool(DataSource dataSource) {
        return new SqlTool(dataSource, sqlDefaultMaxRows, sqlQueryTimeoutSeconds);
    }

    @Bean
    public WebFetchTool webFetchTool() {
        return new WebFetchTool(webfetchProxyHost, webfetchProxyPort);
    }
}
