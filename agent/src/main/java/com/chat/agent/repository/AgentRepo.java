package com.chat.agent.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.chat.agent.llm.LlmClient.Message;

@Repository
public class AgentRepo {

    private static final int MAX_MESSAGES = 20;

    private final Map<String, List<Message>> store = new ConcurrentHashMap<>();

    public List<Message> get(String conversationId) {
        return store.getOrDefault(conversationId, List.of());
    }

    public void append(String conversationId, Message... messages) {
        store.compute(conversationId, (id, old) -> {
            List<Message> list = new ArrayList<>(old == null ? List.of() : old);
            list.addAll(List.of(messages));
            int from = Math.max(0, list.size() - MAX_MESSAGES);
            return List.copyOf(list.subList(from, list.size()));
        });
    }
}
