package com.pms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private String defaultProvider = "deepseek";

    private Map<String, Provider> providers = new LinkedHashMap<>();

    private int timeoutMs = 60_000;

    private int maxToolRounds = 5;

    /** 单次模型响应的最大 token 数，避免运营问答过长。 */
    private int maxTokens = 350;

    public Provider requireProvider(String requestedProvider) {
        String providerName = requestedProvider == null || requestedProvider.isBlank()
                ? defaultProvider
                : requestedProvider.trim().toLowerCase();
        Provider provider = providers.get(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("不支持的模型：" + providerName);
        }
        if (provider.getApiKey() == null || provider.getApiKey().isBlank()) {
            throw new IllegalStateException("未配置 " + providerName + " 的 API Key");
        }
        return provider;
    }

    @Data
    public static class Provider {
        /** OpenAI-compatible API base，包含版本路径但不包含 /chat/completions。 */
        private String baseUrl;

        private String apiKey = "";

        private String model;
    }
}
