package com.chat.agent.llm;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;

@Component
public class GeminiClient implements LlmClient {

    private final String model;
    private final Client client;

    public GeminiClient(@Value("${llm.gemini.model}") String model,
                        @Value("${llm.gemini.api-key}") String apiKey) {
        this.model = model;
        this.client = Client.builder().apiKey(apiKey).build();
    }

    @Override
    public String chat(String systemPrompt, List<Message> history) {
        List<Content> contents = history.stream()
                .map(m -> Content.builder().role(m.role()).parts(Part.fromText(m.text())).build())
                .toList();
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                .build();
        return client.models.generateContent(model, contents, config).text();
    }
}
