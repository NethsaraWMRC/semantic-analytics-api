package com.chat.agent.llm;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One turn sent to the LLM. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LlmMessage {

    public static final String USER = "user";
    public static final String MODEL = "model";

    private String role;
    private String text;

    public static LlmMessage user(String text) {
        return new LlmMessage(USER, text);
    }

    public static LlmMessage model(String text) {
        return new LlmMessage(MODEL, text);
    }
}
