package com.chat.agent.query;

import java.util.List;

import lombok.Getter;

/**
 * The user named something that does not exist exactly, and we are not confident enough
 * to pick for them. Deliberately not an InvalidQueryException: the model cannot fix this,
 * only the person can, so this never goes through the repair loop.
 */
@Getter
public class AmbiguousValueException extends RuntimeException {

    private final String field;
    private final String typed;

    /** close values within the field that was asked for */
    private final List<String> suggestions;

    /** a different field the value suits better, when the model picked the wrong one */
    private final String alternativeField;
    private final List<String> alternativeValues;

    public AmbiguousValueException(String field, String typed, List<String> suggestions,
                                   String alternativeField, List<String> alternativeValues) {
        super("No " + field + " matches '" + typed + "'");
        this.field = field;
        this.typed = typed;
        this.suggestions = suggestions;
        this.alternativeField = alternativeField;
        this.alternativeValues = alternativeValues;
    }

    /** Asked straight to the person, with no LLM call, so a clarification costs nothing. */
    public String toUserMessage() {
        if (!suggestions.isEmpty()) {
            return list("I can't find a " + field + " called \"" + typed + "\". Did you mean one of these?",
                    suggestions, "Tell me which one and I'll run it.");
        }
        if (alternativeField != null) {
            return list("\"" + typed + "\" isn't a " + field + ", but it matches " + alternativeField
                            + ". Did you mean one of these?",
                    alternativeValues, "Say the word and I'll run it by " + alternativeField + " instead.");
        }
        return "I can't find a " + field + " called \"" + typed + "\". Could you check the name?";
    }

    private String list(String opening, List<String> items, String closing) {
        StringBuilder message = new StringBuilder(opening).append("\n");
        for (String item : items) {
            message.append("\n- ").append(item);
        }
        return message.append("\n\n").append(closing).toString();
    }
}
