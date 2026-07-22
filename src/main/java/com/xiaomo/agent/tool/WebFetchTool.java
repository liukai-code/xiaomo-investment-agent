package com.xiaomo.agent.tool;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import com.xiaomo.agent.common.config.HttpClientService;
import com.xiaomo.agent.tool.annotation.ToolBehavior;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.URI;
import java.util.regex.Pattern;

@Slf4j
public class WebFetchTool {

    private static final int MAX_CONTENT_LENGTH = 8000;
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.+");
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final HttpClientService httpClientService;

    public WebFetchTool(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
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

        return httpClientService.executeWithProxyFallback(request);
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
