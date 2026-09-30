package com.chat.agent.semantic;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * One dataset's dictionary: business word -> SQL. Everything the query engine is allowed
 * to know about a database lives behind this interface, so a new dataset (or a different
 * database entirely) is a new implementation and nothing else changes.
 */
public interface SemanticModel {

    String getDataset();

    /** one or two sentences telling the LLM when to choose this dataset over the others. */
    String getDescription();

    /** e.g. "orders o" — the table every query starts from. */
    String getBaseTable();

    Map<String, SemanticField> getMetrics();

    Map<String, SemanticField> getDimensions();

    /** grain name -> SQL template where {} is replaced by the column, e.g. "DATE({})". */
    Map<String, String> getGrains();

    /** the finished aggregate for a metric, e.g. revenue -> "SUM(oi.quantity * oi.unit_price)". */
    String metricSql(String metric);

    /**
     * true when the metric can be added up across groups. A distinct count cannot: a product
     * sold in two regions would be counted twice.
     */
    boolean isAdditive(String metric);

    /** the name of the date dimension, or null if the dataset has none. */
    String dateDimension();

    /** true when grouping that metric by that dimension would produce a misleading number. */
    boolean cannotGroup(String metric, String dimension);

    /** the JOIN clauses these SQL expressions need, in the order they must appear. */
    List<String> joinsFor(Collection<String> sqlExpressions);

    /** how many of the needed joins multiply the row count. More than one would inflate sums. */
    long multiplyingJoins(Collection<String> sqlExpressions);

    /** the metric and dimension list as shown to the LLM. */
    String describe();
}
