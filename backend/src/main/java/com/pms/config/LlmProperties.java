package com.pms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** OpenAI-compatible API base, e.g. https://api.deepseek.com */
    private String baseUrl = "https://api.deepseek.com";

    private String apiKey = "";

    private String model = "deepseek-chat";

    private int timeoutMs = 60_000;

    private int maxToolRounds = 5;

    /** 单次模型响应的最大 token 数，避免运营问答过长。 */
    private int maxTokens = 350;
}
