package com.chat.agent.dto;

import lombok.Data;

/** What the client sends to POST /chat. */
@Data
public class ChatRequest {

    private String conversationId;
    private String message;
}
