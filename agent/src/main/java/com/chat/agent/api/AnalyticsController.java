package com.chat.agent.api;

import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chat.agent.query.DescriptiveQuery;
import com.chat.agent.query.InvalidQueryException;
import com.chat.agent.service.AnalyticsService;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @PostMapping("/descriptive")
    public AnalyticsService.Result descriptive(@RequestBody DescriptiveQuery query) {
        return service.run(query);
    }

    @ExceptionHandler(InvalidQueryException.class)
    public ResponseEntity<Map<String, String>> invalidQuery(InvalidQueryException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> databaseError(DataAccessException e) {
        return ResponseEntity.internalServerError().body(Map.of("error", "The database could not run this query."));
    }
}
