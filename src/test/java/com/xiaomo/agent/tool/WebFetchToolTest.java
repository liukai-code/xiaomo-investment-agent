package com.xiaomo.agent.tool;

import com.xiaomo.agent.common.config.HttpClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebFetchTool 网页抓取工具测试")
class WebFetchToolTest {

    @Mock
    private HttpClientService httpClientService;

    private WebFetchTool tool;

    @BeforeEach
    void setUp() {
        tool = new WebFetchTool(httpClientService);
    }

    @Nested
    @DisplayName("fetchWebpage URL验证")
    class FetchWebpageValidationTest {

        @Test
        @DisplayName("空URL → 返回错误")
        void emptyUrl() {
            String result = tool.fetchWebpage("", null);
            assertTrue(result.contains("不能为空"), "空URL应返回错误");
        }

        @Test
        @DisplayName("null URL → 返回错误")
        void nullUrl() {
            String result = tool.fetchWebpage(null, null);
            assertTrue(result.contains("不能为空"), "null URL应返回错误");
        }

        @Test
        @DisplayName("非http/https协议 → 返回错误")
        void invalidProtocol() {
            String result = tool.fetchWebpage("ftp://example.com", null);
            assertTrue(result.contains("仅支持http和https协议"), "非http/https应返回错误");
        }

        @Test
        @DisplayName("localhost → 被拦截")
        void localhostBlocked() {
            String result = tool.fetchWebpage("http://localhost:8080", null);
            assertTrue(result.contains("禁止访问内网地址"), "localhost应被拦截");
        }

        @Test
        @DisplayName("127.0.0.1 → 被拦截")
        void loopbackBlocked() {
            String result = tool.fetchWebpage("http://127.0.0.1/test", null);
            assertTrue(result.contains("禁止访问内网地址"), "127.0.0.1应被拦截");
        }

        @Test
        @DisplayName("192.168.x.x → 被拦截")
        void privateIp192Blocked() {
            String result = tool.fetchWebpage("http://192.168.1.1/test", null);
            assertTrue(result.contains("禁止访问内网地址"), "192.168应被拦截");
        }

        @Test
        @DisplayName("10.x.x.x → 被拦截")
        void privateIp10Blocked() {
            String result = tool.fetchWebpage("http://10.0.0.1/test", null);
            assertTrue(result.contains("禁止访问内网地址"), "10.x应被拦截");
        }

        @Test
        @DisplayName("172.x.x.x → 被拦截")
        void privateIp172Blocked() {
            String result = tool.fetchWebpage("http://172.16.0.1/test", null);
            assertTrue(result.contains("禁止访问内网地址"), "172.x应被拦截");
        }

        @Test
        @DisplayName("0.0.0.0 → 被拦截")
        void zeroIpBlocked() {
            String result = tool.fetchWebpage("http://0.0.0.0/test", null);
            assertTrue(result.contains("禁止访问内网地址"), "0.0.0.0应被拦截");
        }

        @Test
        @DisplayName("正常公网URL → 不返回验证错误")
        void validPublicUrl() {
            // 这里只测试验证逻辑，实际请求会失败（因为是Mock）
            String result = tool.fetchWebpage("https://www.example.com", null);
            // 不应包含验证错误，可能包含网络请求错误
            assertFalse(result.contains("不能为空"), "正常URL不应返回验证错误");
            assertFalse(result.contains("仅支持http和https协议"), "正常URL不应返回协议错误");
            assertFalse(result.contains("禁止访问内网地址"), "公网URL不应被拦截");
        }
    }

    @Nested
    @DisplayName("fetchArticleContent URL验证")
    class FetchArticleContentValidationTest {

        @Test
        @DisplayName("空URL → 返回错误")
        void emptyUrl() {
            String result = tool.fetchArticleContent("", null);
            assertTrue(result.contains("不能为空"), "空URL应返回错误");
        }

        @Test
        @DisplayName("localhost → 被拦截")
        void localhostBlocked() {
            String result = tool.fetchArticleContent("http://localhost:8080/article", null);
            assertTrue(result.contains("禁止访问内网地址"), "localhost应被拦截");
        }

        @Test
        @DisplayName("非http协议 → 返回错误")
        void invalidProtocol() {
            String result = tool.fetchArticleContent("file:///etc/passwd", null);
            assertTrue(result.contains("仅支持http和https协议"), "非http应返回错误");
        }
    }
}
