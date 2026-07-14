package com.pms.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatRequest {
    private List<ChatMessageDTO> messages = new ArrayList<>();
}
