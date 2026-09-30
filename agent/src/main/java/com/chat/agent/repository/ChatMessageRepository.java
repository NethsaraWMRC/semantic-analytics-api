package com.chat.agent.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.chat.agent.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** newest first, so the caller reverses it to get chronological order. */
    List<ChatMessage> findTop20ByConversationIdOrderByIdDesc(String conversationId);

    List<ChatMessage> findByConversationIdOrderByIdAsc(String conversationId);

    /** every conversation, most recently used first. */
    @Query("select m.conversationId from ChatMessage m group by m.conversationId order by max(m.id) desc")
    List<String> findConversationIds();

    /** used to title each conversation by its opening question, without a query per conversation. */
    List<ChatMessage> findByConversationIdInAndRoleOrderByIdAsc(List<String> conversationIds, String role);
}
