package com.itlk.myclaudecode.tool;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import com.itlk.myclaudecode.tool.annotation.ToolBehavior;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
public class WebFetchTool {

    private static final int MAX_CONTENT_LENGTH = 8000;
    private static final int MAX_RESPONSE_BYTES = 5_000_000;
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.+");
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final OkHttpClient directClient;
    private final OkHttpClient proxyClient;
    private final boolean hasProxy;

    public WebFetchTool() {
        this(null, 0);
    }

    public WebFetchTool(String proxyHost, int proxyPort) {
        this.directClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        if (proxyHost != null && !proxyHost.isBlank()) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            this.proxyClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .proxy(proxy)
                    .build();
            this.hasProxy = true;
            log.info("[WebFetchTool] 已配置代理: {}:{}", proxyHost, proxyPort);
        } else {
            this.proxyClient = null;
            this.hasProxy = false;
        }
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = "抓取指定URL的网页内容并提取可读文本。当联网搜索返回结果后需要阅读全文、用户要求查看某篇文章或网页内容时调用。仅支持http和https协议的URL。")
    public String fetchWebpage(
            @ToolParam(description = "要抓取的网页URL，必须以http://或https://开头") String url,
            @ToolParam(description = "最大返回字符数，默认8000，最大20000", required = false) Integer maxLength) {
        try {
            String urlError = validateUrl(url);
            if (urlError != null) return urlError;

            int maxLen = maxLength != null ? Math.min(maxLength, 20000) : MAX_CONTENT_LENGTH;
            String html = doFetch(url.trim());
            Document doc = Jsoup.parse(html);
            doc.select("script,style,noscript").remove();

            String text = doc.body().text();
            text = truncate(text, maxLen);

            return String.format("=== 网页内容 ===\n标题: %s\n来源: %s\n字符数: %d\n\n%s",
                    doc.title(), url, text.length(), text);
        } catch (RuntimeException e) {
            log.warn("[WebFetchTool] fetchWebpage 失败: {}", e.getMessage());
            return "网页抓取失败: " + e.getMessage();
        } catch (Exception e) {
            log.error("[WebFetchTool] fetchWebpage 异常: {}", e.getMessage(), e);
            return "网页抓取失败: " + e.getMessage();
        }
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = "抓取指定URL的网页并仅提取文章正文内容（去除导航、广告、页脚等杂质）。适合新闻文章、博客帖子、教程等内容型页面。对于纯导航页或首页，请改用fetchWebpage。")
    public String fetchArticleContent(
            @ToolParam(description = "要抓取的文章URL") String url,
            @ToolParam(description = "最大返回字符数，默认8000，最大20000", required = false) Integer maxLength) {
        try {
            String urlError = validateUrl(url);
            if (urlError != null) return urlError;

            int maxLen = maxLength != null ? Math.min(maxLength, 20000) : MAX_CONTENT_LENGTH;
            String html = doFetch(url.trim());
            Document doc = Jsoup.parse(html);

            doc.select("script,style,noscript,nav,header,footer,aside,.sidebar,.ad,.advertisement,.comment,#comments").remove();

            Element container = doc.selectFirst("article");
            if (container == null) container = doc.selectFirst("[role=main]");
            if (container == null) container = doc.selectFirst(".article-content, .post-content, .entry-content, .content-body");
            if (container == null) container = doc.selectFirst("main");
            if (container == null) container = doc.body();

            String text = container.text();
            text = text.replaceAll("\\n{3,}", "\n\n").trim();
            text = truncate(text, maxLen);

            return String.format("=== 文章正文 ===\n标题: %s\n来源: %s\n字符数: %d\n\n%s",
                    doc.title(), url, text.length(), text);
        } catch (RuntimeException e) {
            log.warn("[WebFetchTool] fetchArticleContent 失败: {}", e.getMessage());
            return "网页抓取失败: " + e.getMessage();
        } catch (Exception e) {
            log.error("[WebFetchTool] fetchArticleContent 异常: {}", e.getMessage(), e);
            return "网页抓取失败: " + e.getMessage();
        }
    }

    private String doFetch(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Accept-Encoding", "identity")
                .build();

        if (hasProxy) {
            try {
                return executeRequest(proxyClient, request);
            } catch (java.net.ConnectException e) {
                log.warn("[WebFetchTool] 代理连接失败，回退直连: {}", e.getMessage());
                return executeRequest(directClient, request);
            }
        }
        return executeRequest(directClient, request);
    }

    private String executeRequest(OkHttpClient client, Request request) throws Exception {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP请求失败: " + response.code());
            }

            String contentLength = response.header("Content-Length");
            if (contentLength != null) {
                long size = Long.parseLong(contentLength);
                if (size > MAX_RESPONSE_BYTES) {
                    throw new RuntimeException("响应体过大: " + size + " 字节（限制: " + MAX_RESPONSE_BYTES + "）");
                }
            }

            return response.body().string();
        }
    }

    private String validateUrl(String url) {
        if (url == null || url.isBlank()) return "URL不能为空";
        String trimmed = url.trim();
        if (!URL_PATTERN.matcher(trimmed).matches()) {
            return "仅支持http和https协议的URL: " + trimmed;
        }
        try {
            URI uri = new URI(trimmed);
            String host = uri.getHost();
            if (host == null) return "无效的URL: " + trimmed;
            if (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("0.0.0.0")
                    || host.startsWith("192.168.") || host.startsWith("10.")
                    || host.startsWith("172.")) {
                return "禁止访问内网地址: " + host;
            }
        } catch (Exception e) {
            return "URL格式不正确: " + trimmed;
        }
        return null;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "\n\n[内容已截断，共" + text.length() + "字符]";
    }
}
