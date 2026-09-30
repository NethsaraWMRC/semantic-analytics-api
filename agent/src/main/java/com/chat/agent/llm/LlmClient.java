package com.chat.agent.llm;

import java.util.List;

/** Any LLM provider. Swap the implementation to change provider; nothing else changes. */
public interface LlmClient {

    String chat(String systemPrompt, List<LlmMessage> history);
}
