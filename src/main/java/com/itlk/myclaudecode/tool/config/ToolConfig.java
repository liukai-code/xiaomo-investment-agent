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
import com.itlk.myclaudecode.tool.YangJiBaoTool;
import com.itlk.myclaudecode.tool.astock.*;
import com.itlk.myclaudecode.yjb.service.YjbService;
import com.itlk.myclaudecode.common.config.HttpClientService;
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
    public FinancialDataTool financialDataTool(HttpClientService httpClientService) {
        return new FinancialDataTool(httpClientService);
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
        HttpClientService.Builder builder = new HttpClientService.Builder();
        if (webfetchProxyHost != null && !webfetchProxyHost.isBlank()) {
            builder.proxy(webfetchProxyHost, webfetchProxyPort);
        }
        return new WebFetchTool(builder.build());
    }

    @Bean
    public YangJiBaoTool yangJiBaoTool(YjbService yjbService) {
        return new YangJiBaoTool(yjbService);
    }

    // ===== A股数据工具集 =====

    @Bean
    public AStockQuoteRouterTool aStockQuoteRouterTool(HttpClientService httpClientService) {
        return new AStockQuoteRouterTool(httpClientService);
    }

    @Bean
    public AStockReportRouterTool aStockReportRouterTool(HttpClientService httpClientService,
                                                          EastMoneyRateLimiter emRateLimiter,
                                                          @Value("${astock.iwencai.api-key:}") String iwencaiApiKey) {
        return new AStockReportRouterTool(httpClientService, emRateLimiter, iwencaiApiKey);
    }

    @Bean
    public AStockSignalRouterTool aStockSignalRouterTool(EastMoneyRateLimiter emRateLimiter) {
        return new AStockSignalRouterTool(emRateLimiter);
    }

    @Bean
    public AStockCapitalRouterTool aStockCapitalRouterTool(EastMoneyRateLimiter emRateLimiter,
                                                            org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        return new AStockCapitalRouterTool(emRateLimiter, redisTemplate);
    }

    @Bean
    public AStockNewsRouterTool aStockNewsRouterTool(EastMoneyRateLimiter emRateLimiter) {
        return new AStockNewsRouterTool(emRateLimiter);
    }

    @Bean
    public AStockLimitUpRouterTool aStockLimitUpRouterTool(EastMoneyRateLimiter emRateLimiter) {
        return new AStockLimitUpRouterTool(emRateLimiter);
    }

    @Bean
    public AStockOptionRouterTool aStockOptionRouterTool(HttpClientService httpClientService) {
        return new AStockOptionRouterTool(httpClientService);
    }

    @Bean
    public AStockSentimentRouterTool aStockSentimentRouterTool(EastMoneyRateLimiter emRateLimiter) {
        return new AStockSentimentRouterTool(emRateLimiter);
    }
}
