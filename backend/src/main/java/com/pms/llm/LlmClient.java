package com.pms.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pms.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class LlmClient {

    private final LlmProperties properties;
    private final RestTemplate llmRestTemplate;
    private final ObjectMapper objectMapper;

    public LlmClient(
            LlmProperties properties,
            @Qualifier("llmRestTemplate") RestTemplate llmRestTemplate,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.llmRestTemplate = llmRestTemplate;
        this.objectMapper = objectMapper;
    }

    public JsonNode chat(ArrayNode messages, ArrayNode tools) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("未配置 llm.api-key，无法调用大模型");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
        body.put("max_tokens", Math.max(100, properties.getMaxTokens()));
        body.set("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey().trim());

        String url = trimTrailingSlash(properties.getBaseUrl()) + "/v1/chat/completions";
        try {
            ResponseEntity<String> response = llmRestTemplate.postForEntity(
                    url, new HttpEntity<>(body.toString(), headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("大模型调用失败: HTTP " + response.getStatusCode());
            }
            return objectMapper.readTree(response.getBody());
        } catch (RestClientException e) {
            log.error("LLM request failed: {}", e.getMessage());
            throw new IllegalStateException("大模型调用失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("解析大模型响应失败: " + e.getMessage(), e);
        }
    }

    private static String trimTrailingSlash(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.deepseek.com";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
