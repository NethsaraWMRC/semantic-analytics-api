package com.chat.agent.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.chat.agent.llm.LlmClient;
import com.chat.agent.llm.LlmClient.Message;
import com.chat.agent.llm.Prompts;
import com.chat.agent.query.DescriptiveQuery;
import com.chat.agent.query.InvalidQueryException;
import com.chat.agent.repository.AgentRepo;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AgentService {

    private static final String PARSE_ERROR =
            "Sorry, I couldn't turn that into a valid query. Could you rephrase the question?";

    private final LlmClient llm;
    private final AgentRepo repo;
    private final AnalyticsService analytics;
    private final ObjectMapper mapper;

    public AgentService(LlmClient llm, AgentRepo repo, AnalyticsService analytics, ObjectMapper mapper) {
        this.llm = llm;
        this.repo = repo;
        this.analytics = analytics;
        this.mapper = mapper;
    }

    public String chat(String conversationId, String text) {
        Message userMsg = new Message("user", text);
        List<Message> history = new ArrayList<>(repo.get(conversationId));
        history.add(userMsg);

        // pass 1: question -> JSON query (or a plain-text reply if it is not an analytics question)
        String reply = llm.chat(Prompts.queryPrompt(LocalDate.now()), history);

        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start < 0 || end < start) {
            repo.append(conversationId, userMsg, new Message("model", reply));
            return reply;
        }

        String json = reply.substring(start, end + 1);
        DescriptiveQuery query;
        try {
            query = mapper.readValue(json, DescriptiveQuery.class);
        } catch (JacksonException e) {
            return PARSE_ERROR;
        }

        // memory keeps the JSON query as the model's turn so follow-ups can build on it
        repo.append(conversationId, userMsg, new Message("model", json));
        return explain(text, query);
    }

    // pass 2: result rows (or the validation error) -> plain-language answer
    private String explain(String question, DescriptiveQuery query) {
        String content;
        try {
            content = "Question: " + question
                    + "\nQuery result (JSON): " + mapper.writeValueAsString(analytics.run(query));
        } catch (InvalidQueryException e) {
            content = "Question: " + question
                    + "\nThe query could not be run: " + e.getMessage()
                    + "\nExplain this briefly to the user and suggest how to rephrase.";
        } catch (DataAccessException e) {
            return "Sorry, the database could not run that query.";
        }
        return llm.chat(Prompts.ANSWER_PROMPT, List.of(new Message("user", content)));
    }
}
