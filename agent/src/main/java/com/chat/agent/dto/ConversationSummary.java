package com.chat.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row in the conversation list. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummary {

    private String conversationId;

    /** the opening question, used as the label. */
    private String title;
}
