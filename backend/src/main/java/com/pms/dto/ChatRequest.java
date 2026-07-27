package com.pms.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatRequest {
    private String provider = "deepseek";
    private List<ChatMessageDTO> messages = new ArrayList<>();
}
