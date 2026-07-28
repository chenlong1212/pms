package com.pms.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatRequest {
    private String provider;
    private List<ChatMessageDTO> messages = new ArrayList<>();
}
