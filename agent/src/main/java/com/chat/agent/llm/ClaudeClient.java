package com.chat.agent.llm;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;

/** Anthropic implementation. Active when llm.provider is "claude". */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "claude")
public class ClaudeClient implements LlmClient {

    private static final long MAX_TOKENS = 16000L;

    private final String model;
    private final AnthropicClient client;

    public ClaudeClient(@Value("${llm.claude.model}") String model,
                        @Value("${llm.claude.api-key}") String apiKey) {
        this.model = model;
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }

    @Override
    public String chat(String systemPrompt, List<LlmMessage> history) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(systemPrompt);

        for (LlmMessage message : history) {
            if (LlmMessage.USER.equals(message.getRole())) {
                params.addUserMessage(message.getText());
            } else {
                params.addAssistantMessage(message.getText());
            }
        }

        Message response = client.messages().create(params.build());
        StringBuilder text = new StringBuilder();
        response.content().forEach(block -> block.text().ifPresent(part -> text.append(part.text())));
        return text.toString();
    }
}
