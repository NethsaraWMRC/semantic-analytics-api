package com.chat.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One message as shown in the UI. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatTurn {

    /** "user" or "model" */
    private String role;

    private String text;
}
