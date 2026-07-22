package com.xiaomo.agent.common.config;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class HttpClientService {

    private final OkHttpClient directClient;
    private final OkHttpClient proxyClient;
    private final boolean hasProxy;

    private final int maxRetries;
    private final long baseDelayMs;
    private final long maxDelayMs;

    // Circuit breaker
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final int failureThreshold;
    private final long resetTimeoutMs;

    public HttpClientService() {
        this(new Builder());
    }

    public HttpClientService(Builder builder) {
        this.directClient = new OkHttpClient.Builder()
                .connectTimeout(builder.connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(builder.readTimeoutSeconds, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectionPool(new okhttp3.ConnectionPool(10, 2, TimeUnit.MINUTES))
                .build();

        if (builder.proxyHost != null && !builder.proxyHost.isBlank()) {
            java.net.Proxy proxy = new java.net.Proxy(
                    java.net.Proxy.Type.HTTP,
                    new java.net.InetSocketAddress(builder.proxyHost, builder.proxyPort));
            this.proxyClient = directClient.newBuilder().proxy(proxy).build();
            this.hasProxy = true;
            log.info("[HttpClientService] 已配置代理: {}:{}", builder.proxyHost, builder.proxyPort);
        } else {
            this.proxyClient = null;
            this.hasProxy = false;
        }

        this.maxRetries = builder.maxRetries;
        this.baseDelayMs = builder.baseDelayMs;
        this.maxDelayMs = builder.maxDelayMs;
        this.failureThreshold = builder.failureThreshold;
        this.resetTimeoutMs = builder.resetTimeoutMs;
    }

    public String get(String url, Headers headers) throws Exception {
        Request request = new Request.Builder().url(url).headers(headers).get().build();
        return execute(request);
    }

    /**
     * 使用JDK HttpClient发送GET请求，绕过OkHttp的TLS指纹检测。
     * 适用于对反爬严格的API（如百度金融）。
     */
    public String getWithJdkClient(String url, java.util.Map<String, String> headers) throws Exception {
        HttpClient jdkClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET();
        headers.forEach(reqBuilder::header);
        HttpResponse<String> resp = jdkClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP请求失败: " + resp.statusCode());
        }
        return resp.body();
    }

    public String execute(Request request) throws Exception {
        return executeWithRetry(request, directClient);
    }

    public String executeWithProxyFallback(Request request) throws Exception {
        if (!hasProxy) {
            return execute(request);
        }
        try {
            return executeWithRetry(request, proxyClient);
        } catch (ConnectException e) {
            log.warn("[HttpClientService] 代理连接失败，回退直连: {}", e.getMessage());
            return executeWithRetry(request, directClient);
        }
    }

    public void recordSuccess() {
        failureCount.set(0);
    }

    private String executeWithRetry(Request request, OkHttpClient client) throws Exception {
        checkCircuitBreaker(request);

        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                long delay = calculateDelay(attempt);
                log.info("[HttpClientService] 重试 {}/{}, 等待 {}ms, URL: {}",
                        attempt, maxRetries, delay, request.url());
                Thread.sleep(delay);
            }

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    recordSuccess();
                    String body = response.body() != null ? response.body().string() : "";
                    log.debug("[HttpClientService] 请求成功, URL: {}, 状态: {}",
                            request.url(), response.code());
                    return body;
                }

                int code = response.code();
                if (isRetriableStatus(code) && attempt < maxRetries) {
                    lastException = new IOException("HTTP " + code);
                    log.warn("[HttpClientService] 可重试的HTTP错误: {}, URL: {}", code, request.url());
                    continue;
                }

                recordFailure();
                throw new RuntimeException("HTTP请求失败: " + code);
            } catch (SocketTimeoutException | ConnectException e) {
                lastException = e;
                if (attempt < maxRetries) {
                    log.warn("[HttpClientService] 网络错误，将重试: {}, URL: {}", e.getMessage(), request.url());
                    continue;
                }
                recordFailure();
                throw e;
            } catch (IOException e) {
                lastException = e;
                if (isRetriableException(e) && attempt < maxRetries) {
                    log.warn("[HttpClientService] 可重试异常，将重试: {}, URL: {}", e.getMessage(), request.url());
                    continue;
                }
                recordFailure();
                throw e;
            }
        }

        recordFailure();
        throw lastException != null ? lastException : new IOException("请求失败，已重试 " + maxRetries + " 次");
    }

    private void checkCircuitBreaker(Request request) throws Exception {
        if (failureCount.get() >= failureThreshold) {
            long elapsed = System.currentTimeMillis() - lastFailureTime.get();
            if (elapsed < resetTimeoutMs) {
                long remaining = resetTimeoutMs - elapsed;
                throw new IOException("熔断器已开启，请 " + (remaining / 1000) + " 秒后重试 (URL: " + request.url() + ")");
            }
            log.info("[HttpClientService] 熔断器半开，允许探测请求: {}", request.url());
        }
    }

    private void recordFailure() {
        failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
    }

    private long calculateDelay(int attempt) {
        long delay = baseDelayMs * (1L << (attempt - 1));
        delay = Math.min(delay, maxDelayMs);
        // 加随机抖动 ±25%
        long jitter = delay / 4;
        delay += (long) (Math.random() * 2 * jitter) - jitter;
        return Math.max(delay, 0);
    }

    private boolean isRetriableStatus(int code) {
        return code == 429 || code == 502 || code == 503 || code == 504;
    }

    private boolean isRetriableException(IOException e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("Connection reset") || msg.contains("broken pipe"));
    }

    public static class Builder {
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 15;
        private int maxRetries = 2;
        private long baseDelayMs = 500;
        private long maxDelayMs = 3000;
        private int failureThreshold = 5;
        private long resetTimeoutMs = 60_000;
        private String proxyHost;
        private int proxyPort;

        public Builder connectTimeout(int seconds) { this.connectTimeoutSeconds = seconds; return this; }
        public Builder readTimeout(int seconds) { this.readTimeoutSeconds = seconds; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder baseDelayMs(long ms) { this.baseDelayMs = ms; return this; }
        public Builder maxDelayMs(long ms) { this.maxDelayMs = ms; return this; }
        public Builder failureThreshold(int threshold) { this.failureThreshold = threshold; return this; }
        public Builder resetTimeoutMs(long ms) { this.resetTimeoutMs = ms; return this; }
        public Builder proxy(String host, int port) { this.proxyHost = host; this.proxyPort = port; return this; }
        public HttpClientService build() { return new HttpClientService(this); }
    }
}
