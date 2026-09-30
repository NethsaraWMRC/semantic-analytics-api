package com.chat.agent.query;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** A finished SQL statement and the values to bind to its ? placeholders. */
@Getter
@AllArgsConstructor
public class BuiltSql {

    private final String sql;
    private final List<Object> params;
}
