package com.chat.agent.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.chat.agent.dto.AnalyticsResult;
import com.chat.agent.dto.ChatTurn;
import com.chat.agent.dto.ConversationSummary;
import com.chat.agent.dto.DescriptiveQuery;
import com.chat.agent.entity.ChatMessage;
import com.chat.agent.llm.LlmClient;
import com.chat.agent.llm.LlmMessage;
import com.chat.agent.llm.PromptLoader;
import com.chat.agent.query.InvalidQueryException;
import com.chat.agent.query.PeriodResolver;
import com.chat.agent.repository.ChatMessageRepository;
import com.chat.agent.semantic.SemanticRegistry;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The chat flow. Pass 1 turns the question into a structured query, the analytics service
 * runs it, and pass 2 turns the rows back into a sentence.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final int MAX_ATTEMPTS = 2;

    private static final String PARSE_ERROR =
            "Sorry, I couldn't turn that into a valid query. Could you rephrase the question?";

    private final LlmClient llm;
    private final ChatMessageRepository history;
    private final AnalyticsService analytics;
    private final ObjectMapper mapper;
    private final PromptLoader prompts;
    private final SemanticRegistry registry;
    private final String defaultDataset;

    public AgentService(LlmClient llm, ChatMessageRepository history, AnalyticsService analytics,
                        ObjectMapper mapper, PromptLoader prompts, SemanticRegistry registry,
                        @Value("${analytics.dataset}") String defaultDataset) {
        this.llm = llm;
        this.history = history;
        this.analytics = analytics;
        this.mapper = mapper;
        this.prompts = prompts;
        this.registry = registry;
        this.defaultDataset = registry.require(defaultDataset).getDataset();
    }

    public String chat(String conversationId, String question) {
        List<LlmMessage> conversation = loadHistory(conversationId);
        conversation.add(LlmMessage.user(question));

        String reply = llm.chat(queryPrompt(), conversation);
        String json = extractJson(reply);

        // not a data question: the LLM answered in plain words, so pass it straight through
        if (json == null) {
            save(conversationId, question, reply, reply);
            return reply;
        }

        // a rejected query gets one more go, with the validator's complaint handed back to the model
        for (int attempt = 1; ; attempt++) {
            DescriptiveQuery query;
            try {
                query = mapper.readValue(json, DescriptiveQuery.class);
            } catch (JacksonException e) {
                return PARSE_ERROR;
            }

            AnalyticsResult result;
            try {
                result = analytics.run(query);
            } catch (InvalidQueryException e) {
                String corrected = attempt < MAX_ATTEMPTS ? repair(conversation, json, e.getMessage()) : null;
                if (corrected == null) {
                    return explainFailure(question, e.getMessage());
                }
                json = corrected;
                continue;
            } catch (DataAccessException e) {
                return "Sorry, the database could not run that query.";
            }

            String answer = explain(question, result);

            // the LLM is given the JSON query back as history, so follow-ups build on it;
            // the person sees the written answer
            save(conversationId, question, json, answer);
            return answer;
        }
    }

    /** Shows the model what the validator objected to and asks for a corrected query. */
    private String repair(List<LlmMessage> conversation, String rejectedJson, String reason) {
        List<LlmMessage> withFeedback = new ArrayList<>(conversation);
        withFeedback.add(LlmMessage.model(rejectedJson));
        withFeedback.add(LlmMessage.user("That query was rejected: " + reason
                + " Fix it and reply with the corrected JSON only."));

        log.info("repairing rejected query: {}", reason);
        return extractJson(llm.chat(queryPrompt(), withFeedback));
    }

    public List<ConversationSummary> conversations() {
        List<String> ids = history.findConversationIds();
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<String, String> titles = new HashMap<>();
        for (ChatMessage message : history.findByConversationIdInAndRoleOrderByIdAsc(ids, LlmMessage.USER)) {
            titles.putIfAbsent(message.getConversationId(), message.getContent());
        }

        List<ConversationSummary> summaries = new ArrayList<>();
        for (String id : ids) {
            summaries.add(new ConversationSummary(id, titles.getOrDefault(id, "New chat")));
        }
        return summaries;
    }

    public List<ChatTurn> messages(String conversationId) {
        List<ChatTurn> turns = new ArrayList<>();
        for (ChatMessage message : history.findByConversationIdOrderByIdAsc(conversationId)) {
            String text = message.getDisplayText() == null ? message.getContent() : message.getDisplayText();
            turns.add(new ChatTurn(message.getRole(), text));
        }
        return turns;
    }

    private String explain(String question, AnalyticsResult result) {
        String content = "Question: " + question
                + "\nQuery result (JSON): " + mapper.writeValueAsString(result);
        return llm.chat(prompts.get("answer-prompt"), List.of(LlmMessage.user(content)));
    }

    private String explainFailure(String question, String reason) {
        String content = "Question: " + question
                + "\nThe query could not be run: " + reason
                + "\nExplain this briefly to the user and suggest how to rephrase.";
        return llm.chat(prompts.get("answer-prompt"), List.of(LlmMessage.user(content)));
    }

    private List<LlmMessage> loadHistory(String conversationId) {
        List<ChatMessage> saved = history.findTop20ByConversationIdOrderByIdDesc(conversationId);
        Collections.reverse(saved);

        List<LlmMessage> conversation = new ArrayList<>();
        for (ChatMessage message : saved) {
            conversation.add(new LlmMessage(message.getRole(), message.getContent()));
        }
        return conversation;
    }

    private void save(String conversationId, String question, String modelContent, String answer) {
        history.saveAll(List.of(
                new ChatMessage(conversationId, LlmMessage.USER, question, question),
                new ChatMessage(conversationId, LlmMessage.MODEL, modelContent, answer)));
    }

    private String queryPrompt() {
        return prompts.render("query-prompt", Map.of(
                "today", LocalDate.now().toString(),
                "datasets", registry.describeAll(),
                "defaultDataset", defaultDataset,
                "periods", String.join(", ", PeriodResolver.PERIODS)));
    }

    private static String extractJson(String reply) {
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        return start < 0 || end < start ? null : reply.substring(start, end + 1);
    }
}
