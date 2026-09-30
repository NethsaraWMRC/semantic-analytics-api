package com.chat.agent.semantic;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/** Loads every semantic model in resources/semantic/. Drop in a JSON file to add a dataset. */
@Component
public class SemanticRegistry {

    private final Map<String, SemanticModel> models = new LinkedHashMap<>();

    public SemanticRegistry(ObjectMapper mapper) throws IOException {
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:semantic/*.json")) {
            try (InputStream in = resource.getInputStream()) {
                JsonSemanticModel model = mapper.readValue(in, JsonSemanticModel.class);
                models.put(model.getDataset(), model);
            }
        }
        if (models.isEmpty()) {
            throw new IllegalStateException("No semantic models found in resources/semantic/");
        }
    }

    public Optional<SemanticModel> find(String dataset) {
        return Optional.ofNullable(models.get(dataset));
    }

    public SemanticModel require(String dataset) {
        return find(dataset).orElseThrow(() ->
                new IllegalArgumentException("Unknown dataset '" + dataset + "'. Available: " + datasets()));
    }

    public Set<String> datasets() {
        return models.keySet();
    }

    /** every dataset's purpose and vocabulary, as shown to the LLM so it can pick one. */
    public String describeAll() {
        StringBuilder text = new StringBuilder();
        for (SemanticModel model : models.values()) {
            text.append("=== dataset: ").append(model.getDataset()).append(" ===\n");
            if (model.getDescription() != null) {
                text.append(model.getDescription()).append("\n");
            }
            text.append(model.describe()).append("\n");
        }
        return text.toString();
    }
}
