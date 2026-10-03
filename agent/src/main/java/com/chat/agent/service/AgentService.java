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
import org.springframework.transaction.annotation.Transactional;

import com.chat.agent.dto.AnalyticsResult;
import com.chat.agent.dto.ChatTurn;
import com.chat.agent.dto.ConversationSummary;
import com.chat.agent.dto.DescriptiveQuery;
import com.chat.agent.entity.ChatMessage;
import com.chat.agent.llm.LlmClient;
import com.chat.agent.llm.LlmMessage;
import com.chat.agent.llm.PromptLoader;
import com.chat.agent.query.AmbiguousValueException;
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

    /** long enough for a follow-up offer, short enough that a table never slips in */
    private static final int MAX_OFFER_LENGTH = 200;

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
            } catch (AmbiguousValueException e) {
                // the model cannot fix a name only the person knows, so ask them directly.
                // answered from code, so a clarification costs no tokens at all.
                String clarification = e.toUserMessage();
                save(conversationId, question, clarification, clarification);
                return clarification;
            } catch (DataAccessException e) {
                return "Sorry, the database could not run that query.";
            }

            String answer = explain(question, query, result);

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

    @Transactional
    public void delete(String conversationId) {
        history.deleteByConversationId(conversationId);
    }

    public List<ChatTurn> messages(String conversationId) {
        List<ChatTurn> turns = new ArrayList<>();
        for (ChatMessage message : history.findByConversationIdOrderByIdAsc(conversationId)) {
            String text = message.getDisplayText() == null ? message.getContent() : message.getDisplayText();
            turns.add(new ChatTurn(message.getRole(), text));
        }
        return turns;
    }

    private String explain(String question, DescriptiveQuery query, AnalyticsResult result) {
        String content = "Question: " + question
                + "\nWhat was measured: " + summarise(query)
                + "\nBreakdowns available on this dataset: " + breakdowns(query)
                + "\nQuery result (JSON): " + mapper.writeValueAsString(result);
        return llm.chat(prompts.get("answer-prompt"), List.of(LlmMessage.user(content)));
    }

    /**
     * Dimension names only, never their values. Lets the answer offer a next step that this
     * dataset can actually deliver, instead of inventing a field that does not exist.
     */
    private String breakdowns(DescriptiveQuery query) {
        String dataset = query.getDataset() == null ? defaultDataset : query.getDataset();
        return registry.find(dataset)
                .map(model -> String.join(", ", model.getDimensions().keySet()))
                .orElse("none");
    }

    /**
     * A plain-words version of the query that ran. Without it a follow-up like "the first one"
     * produces a bare number that nobody can check.
     */
    private String summarise(DescriptiveQuery query) {
        StringBuilder summary = new StringBuilder(String.join(", ", query.getMetrics()));

        if (!query.getDimensions().isEmpty()) {
            List<String> fields = new ArrayList<>();
            query.getDimensions().forEach(dimension -> fields.add(dimension.getField()));
            summary.append(" by ").append(String.join(", ", fields));
        }

        if (!query.getFilters().isEmpty()) {
            List<String> conditions = new ArrayList<>();
            query.getFilters().forEach(filter -> conditions.add(
                    filter.getField() + " " + filter.getOperator() + " " + filter.getValue()));
            summary.append(" where ").append(String.join(" and ", conditions));
        }

        return summary + " (dataset: " + query.getDataset() + ")";
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
            conversation.add(new LlmMessage(message.getRole(), forModel(message)));
        }
        return conversation;
    }

    /**
     * A model turn is stored as the JSON query, because that is what follow-ups build on.
     * But the person replied to the words they saw, so a trailing offer like "Want to see each
     * product's share?" has to travel too, or a bare "yes" has nothing to refer to.
     */
    private String forModel(ChatMessage message) {
        if (!LlmMessage.MODEL.equals(message.getRole())) {
            return message.getContent();
        }
        String offer = trailingQuestion(message.getDisplayText());
        return offer == null ? message.getContent()
                : message.getContent() + "\n(You then asked the user: " + offer + ")";
    }

    /** The last line of the answer when it is a question, which is how the answer prompt ends one. */
    private String trailingQuestion(String answer) {
        if (answer == null) {
            return null;
        }
        String[] lines = answer.strip().split("\n");
        String last = lines[lines.length - 1].strip();
        return last.endsWith("?") && last.length() <= MAX_OFFER_LENGTH ? last : null;
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
