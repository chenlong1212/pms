package com.pms.controller;

import com.pms.dto.ChatMessageDTO;
import com.pms.dto.ChatRequest;
import com.pms.llm.ChatAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatAgentService chatAgentService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<ChatMessageDTO> messages = request != null ? request.getMessages() : List.of();
            String answer = chatAgentService.chat(messages);
            result.put("code", 200);
            result.put("data", Map.of(
                    "role", "assistant",
                    "content", answer
            ));
            result.put("message", "success");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            result.put("code", 400);
            result.put("data", null);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (IllegalStateException e) {
            result.put("code", 502);
            result.put("data", null);
            result.put("message", e.getMessage());
            return ResponseEntity.status(502).body(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("data", null);
            result.put("message", "问答服务异常: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}
