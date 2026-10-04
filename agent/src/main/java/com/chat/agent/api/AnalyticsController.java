package com.chat.agent.api;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chat.agent.dto.AnalyticsResult;
import com.chat.agent.dto.DescriptiveQuery;
import com.chat.agent.dto.DiagnosticQuery;
import com.chat.agent.dto.DiagnosticResult;
import com.chat.agent.dto.ErrorResponse;
import com.chat.agent.query.AmbiguousValueException;
import com.chat.agent.query.InvalidQueryException;
import com.chat.agent.service.AnalyticsService;
import com.chat.agent.service.DiagnosticService;

/** The structured query API. Same engine as the chat, but you write the JSON yourself. */
@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService service;
    private final DiagnosticService diagnostics;

    public AnalyticsController(AnalyticsService service, DiagnosticService diagnostics) {
        this.service = service;
        this.diagnostics = diagnostics;
    }

    @PostMapping("/descriptive")
    public AnalyticsResult descriptive(@RequestBody DescriptiveQuery query) {
        return service.run(query);
    }

    /** Explains a change: what moved, and which segments account for it. */
    @PostMapping("/diagnostic")
    public DiagnosticResult diagnostic(@RequestBody DiagnosticQuery query) {
        return diagnostics.run(query);
    }

    @ExceptionHandler(InvalidQueryException.class)
    public ResponseEntity<ErrorResponse> invalidQuery(InvalidQueryException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(AmbiguousValueException.class)
    public ResponseEntity<ErrorResponse> ambiguousValue(AmbiguousValueException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.toUserMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> databaseError(DataAccessException e) {
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("The database could not run this query."));
    }
}
