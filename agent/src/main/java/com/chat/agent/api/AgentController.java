package com.chat.agent.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chat.agent.dto.ChatRequest;
import com.chat.agent.dto.ChatResponse;
import com.chat.agent.dto.ChatTurn;
import com.chat.agent.dto.ConversationSummary;
import com.chat.agent.service.AgentService;

/** Ask a question in plain English. */
@RestController
@RequestMapping("/chat")
public class AgentController {

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }
        return new ChatResponse(conversationId, service.chat(conversationId, request.getMessage()));
    }

    @GetMapping("/conversations")
    public List<ConversationSummary> conversations() {
        return service.conversations();
    }

    @GetMapping("/conversations/{conversationId}")
    public List<ChatTurn> messages(@PathVariable String conversationId) {
        return service.messages(conversationId);
    }
}
