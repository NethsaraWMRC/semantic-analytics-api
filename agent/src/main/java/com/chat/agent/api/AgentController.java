package com.chat.agent.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chat.agent.service.AgentService;

@RestController
@RequestMapping("/chat")
public class AgentController {

    public record ChatRequest(String conversationId, String message) {}
    public record ChatResponse(String conversationId, String reply) {}

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String id = (request.conversationId() == null || request.conversationId().isBlank())
                ? UUID.randomUUID().toString()
                : request.conversationId();
        return new ChatResponse(id, service.chat(id, request.message()));
    }
}
