package com.chat.agent.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One turn of a conversation, stored so the chat remembers after a restart. */
@Entity
@Table(name = "agent_chat_messages")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

    /** "user" or "model" */
    @Column(nullable = false)
    private String role;

    /** what the LLM is given back as history. For a model turn this is the structured query. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** what a human should see. Same as content for questions, the written answer for replies. */
    @Column(name = "display_text", columnDefinition = "TEXT")
    private String displayText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ChatMessage(String conversationId, String role, String content, String displayText) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.displayText = displayText;
    }
}
