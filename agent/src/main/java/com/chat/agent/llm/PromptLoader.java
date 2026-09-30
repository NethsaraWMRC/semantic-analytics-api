package com.chat.agent.llm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Reads every prompt in resources/prompts/. Edit a .txt file and restart —
 * no Java change. Placeholders look like {{name}}.
 */
@Component
public class PromptLoader {

    private final Map<String, String> prompts = new HashMap<>();

    public PromptLoader() throws IOException {
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:prompts/*.txt")) {
            try (InputStream in = resource.getInputStream()) {
                String name = resource.getFilename().replace(".txt", "");
                prompts.put(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    public String get(String name) {
        String prompt = prompts.get(name);
        if (prompt == null) {
            throw new IllegalArgumentException("No prompt named '" + name + "' in resources/prompts/");
        }
        return prompt;
    }

    public String render(String name, Map<String, String> values) {
        String prompt = get(name);
        for (Map.Entry<String, String> value : values.entrySet()) {
            prompt = prompt.replace("{{" + value.getKey() + "}}", value.getValue());
        }
        return prompt;
    }
}
