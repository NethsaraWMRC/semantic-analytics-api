package com.chat.agent.semantic;

import lombok.Data;

/** One joinable table in a dataset. */
@Data
public class SemanticTable {

    /** the full JOIN clause, e.g. "JOIN customers c ON c.id = o.customer_id". */
    private String join;

    /**
     * "one" (default) when the join keeps the row count the same, "many" when one base row
     * can match several rows here. Two "many" joins in one query would multiply the numbers,
     * so the engine refuses that combination.
     */
    private String cardinality = "one";

    public boolean isMultiplying() {
        return "many".equals(cardinality);
    }
}
