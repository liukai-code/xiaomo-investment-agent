package com.itlk.myclaudecode.tool.astock;

import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class EastMoneyRateLimiter {

    private final long minIntervalMs;
    private final long jitterMaxMs;
    private final Random random = new Random();
    private long lastCallTime = 0;

    private final OkHttpClient emClient;

    private static final String EM_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";

    public EastMoneyRateLimiter(
            @Value("${astock.eastmoney.min-interval-ms:1000}") long minIntervalMs,
            @Value("${astock.eastmoney.jitter-max-ms:500}") long jitterMaxMs,
            @Value("${astock.eastmoney.connect-timeout-seconds:10}") int connectTimeout,
            @Value("${astock.eastmoney.read-timeout-seconds:15}") int readTimeout) {
        this.minIntervalMs = minIntervalMs;
        this.jitterMaxMs = jitterMaxMs;
        this.emClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 2, TimeUnit.MINUTES))
                .followRedirects(true)
                .build();
    }

    public synchronized String get(String url, Map<String, String> headers) throws Exception {
        waitForInterval();
        Request.Builder reqBuilder = new Request.Builder().url(url).get();
        reqBuilder.header("User-Agent", EM_UA);
        if (headers != null) {
            headers.forEach(reqBuilder::header);
        }
        Request request = reqBuilder.build();
        return executeWithRetry(request, true);
    }

    public synchronized String post(String url, String jsonBody,
                                    Map<String, String> headers) throws Exception {
        waitForInterval();
        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
        Request.Builder reqBuilder = new Request.Builder().url(url).post(body);
        reqBuilder.header("User-Agent", EM_UA);
        if (headers != null) {
            headers.forEach(reqBuilder::header);
        }
        Request request = reqBuilder.build();
        return executeWithRetry(request, false);
    }

    private void waitForInterval() throws InterruptedException {
        long now = System.currentTimeMillis();
        long elapsed = now - lastCallTime;
        long waitNeeded = minIntervalMs - elapsed;
        if (waitNeeded > 0) {
            long jitter = (long) (random.nextDouble() * jitterMaxMs);
            log.debug("[EastMoneyRateLimiter] 限流等待 {}ms + 抖动 {}ms", waitNeeded, jitter);
            Thread.sleep(waitNeeded + jitter);
        }
    }

    private String executeWithRetry(Request request, boolean retryOnServer) throws Exception {
        try (Response response = emClient.newCall(request).execute()) {
            lastCallTime = System.currentTimeMillis();
            int code = response.code();
            if (code == 403) {
                throw new RuntimeException("东财 403 风控触发，请降低请求频率");
            }
            if (retryOnServer && (code == 429 || code >= 500)) {
                return retryWithBackoff(request, 3);
            }
            if (!response.isSuccessful()) {
                throw new RuntimeException("东财 HTTP " + code);
            }
            ResponseBody respBody = response.body();
            return respBody != null ? respBody.string() : "";
        } catch (IOException e) {
            lastCallTime = System.currentTimeMillis();
            throw e;
        }
    }

    private String retryWithBackoff(Request request, int maxRetries) throws Exception {
        Exception lastException = null;
        for (int i = 0; i < maxRetries; i++) {
            long delay = 600L * (1L << i);
            log.info("[EastMoneyRateLimiter] 重试第{}次，等待{}ms", i + 1, delay);
            Thread.sleep(delay);
            try (Response resp = emClient.newCall(request).execute()) {
                lastCallTime = System.currentTimeMillis();
                if (resp.isSuccessful()) {
                    ResponseBody respBody = resp.body();
                    return respBody != null ? respBody.string() : "";
                }
                if (resp.code() == 403) break;
                lastException = new RuntimeException("东财重试 HTTP " + resp.code());
            }
        }
        throw lastException != null ? lastException : new IOException("东财重试耗尽");
    }
}
