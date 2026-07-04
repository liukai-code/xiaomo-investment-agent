package com.itlk.myclaudecode.yjb.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class YjbApiClient {

    private static final String BASE_URL = "http://browser-plug-api.yangjibao.com";
    private static final String SECRET = "YxmKSrQR4uoJ5lOoWIhcbd7SlUEh9OOc";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 公开方法 ====================

    public QrCodeResponse getQrCode() throws IOException {
        String path = "/qr_code";
        JsonNode data = doGet(path, "", null);
        QrCodeResponse resp = new QrCodeResponse();
        resp.id = data.get("id").asText();
        resp.url = data.get("url").asText();
        return resp;
    }

    public QrCodeStateResponse getQrCodeState(String qrId) throws IOException {
        String path = "/qr_code_state/" + qrId;
        JsonNode data = doGet(path, "", null);
        QrCodeStateResponse resp = new QrCodeStateResponse();
        resp.state = data.get("state").asInt();
        if (data.has("token") && !data.get("token").isNull()) {
            resp.token = data.get("token").asText();
        }
        return resp;
    }

    public List<UserAccountResponse> getUserAccounts(String token) throws IOException {
        String path = "/user_account";
        JsonNode data = doGet(path, token, null);
        JsonNode list = data.has("list") ? data.get("list") : data;
        if (!list.isArray()) {
            return Collections.emptyList();
        }
        List<UserAccountResponse> result = new ArrayList<>();
        for (JsonNode item : list) {
            UserAccountResponse acct = new UserAccountResponse();
            acct.id = item.get("id").asText();
            acct.title = item.get("title").asText();
            acct.count = item.has("count") ? item.get("count").asInt() : 0;
            result.add(acct);
        }
        return result;
    }

    public AccountCollectResponse getAccountCollect(String token, String accountId) throws IOException {
        String path = "/account_collect";
        JsonNode params = objectMapper.createObjectNode().put("account_id", accountId);
        JsonNode data = doGet(path, token, params);
        JsonNode arr = data.has("account_data") ? data.get("account_data") : data;
        JsonNode item = arr.isArray() && arr.size() > 0 ? arr.get(0) : data;
        return objectMapper.treeToValue(item, AccountCollectResponse.class);
    }

    public List<FundHoldResponse> getFundHoldings(String token, String accountId) throws IOException {
        String path = "/fund_hold";
        JsonNode params = objectMapper.createObjectNode().put("account_id", accountId);
        JsonNode data = doGet(path, token, params);
        if (!data.isArray()) {
            return Collections.emptyList();
        }
        List<FundHoldResponse> result = new ArrayList<>();
        for (JsonNode item : data) {
            result.add(objectMapper.treeToValue(item, FundHoldResponse.class));
        }
        return result;
    }

    // ==================== 公网行情 API ====================

    private static final String PUBLIC_BASE_URL = "https://app-api.yangjibao.com";

    public List<FundValuationResponse> getFundValuations(List<String> fundIds) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode fundsArray = mapper.createArrayNode();
        for (String id : fundIds) {
            ObjectNode fund = mapper.createObjectNode();
            fund.put("fund_id", Integer.parseInt(id));
            fund.put("data_source", "1");
            fundsArray.add(fund);
        }
        ObjectNode body = mapper.createObjectNode();
        body.set("funds", fundsArray);

        Request request = new Request.Builder()
                .url(PUBLIC_BASE_URL + "/market/v1/fund/batch")
                .headers(new Headers.Builder()
                        .add("Content-Type", "application/json")
                        .add("User-Agent", "YJB/2.0.4")
                        .build())
                .post(okhttp3.RequestBody.create(
                        body.toString(),
                        okhttp3.MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("批量估值请求失败: HTTP " + response.code());
            }
            String respBody = response.body() != null ? response.body().string() : "";
            JsonNode root = objectMapper.readTree(respBody);

            int code = root.has("code") ? root.get("code").asInt() : 0;
            if (code != 200) {
                String message = root.has("message") ? root.get("message").asText() : "未知错误";
                throw new IOException("批量估值返回错误: code=" + code + ", message=" + message);
            }

            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                return Collections.emptyList();
            }
            List<FundValuationResponse> result = new ArrayList<>();
            for (JsonNode item : data) {
                result.add(objectMapper.treeToValue(item, FundValuationResponse.class));
            }
            return result;
        }
    }

    // ==================== 内部方法 ====================

    private JsonNode doGet(String path, String token, JsonNode queryParams) throws IOException {
        Headers headers = buildHeaders(path, token);

        HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + path).newBuilder();
        if (queryParams != null && queryParams.isObject()) {
            queryParams.fields().forEachRemaining(entry ->
                    urlBuilder.addQueryParameter(entry.getKey(), entry.getValue().asText()));
        }

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .headers(headers)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("YJB API 请求失败: HTTP " + response.code());
            }
            String body = response.body() != null ? response.body().string() : "";
            JsonNode root = objectMapper.readTree(body);

            int code = root.has("code") ? root.get("code").asInt() : 0;
            if (code != 200) {
                String message = root.has("message") ? root.get("message").asText() : "未知错误";
                throw new IOException("YJB API 返回错误: code=" + code + ", message=" + message);
            }
            return root.get("data");
        }
    }

    private Headers buildHeaders(String path, String token) {
        if (token == null) token = "";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signPath = path.split("\\?")[0];
        String sign = md5(signPath + token + timestamp + SECRET);
        return new Headers.Builder()
                .add("Authorization", token)
                .add("Request-Time", timestamp)
                .add("Request-Sign", sign)
                .add("Content-Type", "application/json")
                .build();
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== 内部 DTO ====================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QrCodeResponse {
        public String id;
        public String url;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QrCodeStateResponse {
        public int state;
        public String token;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserAccountResponse {
        public String id;
        public String title;
        public int count;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AccountCollectResponse {
        @JsonProperty("hold_cost")
        public BigDecimal holdCost;
        @JsonProperty("today_income")
        public BigDecimal todayIncome;
        @JsonProperty("today_income_rate")
        public BigDecimal todayIncomeRate;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FundHoldResponse {
        @JsonProperty("fund_id")
        public String fundId;
        public String code;
        @JsonProperty("short_name")
        public String shortName;
        public BigDecimal money;
        @JsonProperty("hold_earn")
        public BigDecimal holdEarn;
        @JsonProperty("hold_share")
        public BigDecimal holdShare;
        @JsonProperty("hold_cost")
        public BigDecimal holdCost;
        @JsonProperty("cost_money")
        public BigDecimal costMoney;
        @JsonProperty("hold_day")
        public String holdDay;
        public String category;
        @JsonProperty("market_type")
        public String marketType;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FundValuationResponse {
        @JsonProperty("fund_id")
        public int fundId;
        public String code;
        @JsonProperty("short_name")
        public String shortName;
        @JsonProperty("nv_info")
        public NvInfo nvInfo;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class NvInfo {
            public String dwjz;
            public String rzzl;
            public String vgszzl;
            public String jzrq;
        }
    }
}
