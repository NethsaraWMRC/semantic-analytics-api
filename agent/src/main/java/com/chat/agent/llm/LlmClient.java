package com.chat.agent.llm;

import java.util.List;

public interface LlmClient {

    record Message(String role, String text) {} // role: "user" or "model"

    String chat(String systemPrompt, List<Message> history);
}
