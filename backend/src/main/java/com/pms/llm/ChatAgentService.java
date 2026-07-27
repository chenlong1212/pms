package com.pms.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pms.config.LlmProperties;
import com.pms.dto.ChatMessageDTO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAgentService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_HISTORY_MESSAGES = 10;
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final LlmClient llmClient;
    private final ChatTools chatTools;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;

    private String promptTemplate;

    @PostConstruct
    void loadPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/ops-assistant.txt");
            promptTemplate = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("无法加载 system prompt: prompts/ops-assistant.txt", e);
        }
    }

    public String chat(String provider, List<ChatMessageDTO> userMessages) {
        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode system = objectMapper.createObjectNode();
        system.put("role", "system");
        system.put("content", buildSystemPrompt());
        messages.add(system);

        int historyStart = Math.max(0, userMessages.size() - MAX_HISTORY_MESSAGES);
        for (int i = historyStart; i < userMessages.size(); i++) {
            ChatMessageDTO msg = userMessages.get(i);
            if (msg == null || msg.getContent() == null || msg.getContent().isBlank()) {
                continue;
            }
            String role = msg.getRole() == null ? "user" : msg.getRole().trim().toLowerCase();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            String content = msg.getContent().trim();
            if (content.length() > MAX_MESSAGE_LENGTH) {
                throw new IllegalArgumentException("单条消息不能超过 " + MAX_MESSAGE_LENGTH + " 字");
            }
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", role);
            node.put("content", content);
            messages.add(node);
        }

        if (messages.size() <= 1) {
            throw new IllegalArgumentException("请输入问题");
        }

        ArrayNode tools = chatTools.toolDefinitions();
        int maxRounds = Math.max(1, llmProperties.getMaxToolRounds());

        for (int round = 0; round < maxRounds; round++) {
            JsonNode response = llmClient.chat(provider, messages, tools);
            JsonNode choice = response.path("choices").path(0);
            JsonNode message = choice.path("message");
            if (message.isMissingNode() || message.isNull()) {
                throw new IllegalStateException("大模型未返回有效消息");
            }

            messages.add(message.deepCopy());

            JsonNode toolCalls = message.path("tool_calls");
            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                String content = message.path("content").asText("");
                if (content == null || content.isBlank()) {
                    throw new IllegalStateException("大模型未返回文本回答");
                }
                return content.trim();
            }

            for (JsonNode toolCall : toolCalls) {
                String toolCallId = toolCall.path("id").asText("");
                String name = toolCall.path("function").path("name").asText("");
                String argumentsRaw = toolCall.path("function").path("arguments").asText("{}");
                JsonNode args;
                try {
                    args = objectMapper.readTree(
                            argumentsRaw == null || argumentsRaw.isBlank() ? "{}" : argumentsRaw);
                } catch (Exception e) {
                    args = objectMapper.createObjectNode();
                }
                log.info("Executing tool {} args={}", name, argumentsRaw);
                String result = chatTools.execute(name, args);

                ObjectNode toolResult = objectMapper.createObjectNode();
                toolResult.put("role", "tool");
                toolResult.put("tool_call_id", toolCallId);
                toolResult.put("content", result);
                messages.add(toolResult);
            }
        }

        throw new IllegalStateException("工具调用轮次过多，请简化问题后重试");
    }

    private String buildSystemPrompt() {
        String today = LocalDate.now(ZONE).toString();
        return promptTemplate.replace("{{today}}", today);
    }
}
