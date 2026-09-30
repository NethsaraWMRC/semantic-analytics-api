package com.chat.agent.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/**
 * Groq implementation. Active when llm.provider is "groq".
 * Groq speaks the OpenAI chat format, so this is a plain HTTP call, no SDK needed.
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "groq")
public class GroqClient implements LlmClient {

    private final String model;
    private final RestClient client;

    public GroqClient(@Value("${llm.groq.base-url}") String baseUrl,
                      @Value("${llm.groq.model}") String model,
                      @Value("${llm.groq.api-key}") String apiKey) {
        this.model = model;
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Override
    public String chat(String systemPrompt, List<LlmMessage> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        for (LlmMessage message : history) {
            // Groq calls the model's own turns "assistant"
            String role = LlmMessage.USER.equals(message.getRole()) ? "user" : "assistant";
            messages.add(Map.of("role", role, "content", message.getText()));
        }

        ChatCompletion response = client.post()
                .uri("/chat/completions")
                .body(Map.of("model", model, "messages", messages))
                .retrieve()
                .body(ChatCompletion.class);

        return response.getChoices().get(0).getMessage().getContent();
    }

    /** Only the part of Groq's reply we use; the rest of the payload is ignored. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatCompletion {
        private List<Choice> choices;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Message message;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String content;
    }
}
