package com.chat.agent.semantic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Business word -> database expression. Data only; SQL assembly lives in SqlBuilder. */
public final class SemanticLayer {

    public record Metric(String expr, Set<String> joins, Set<String> incompatibleDimensions, String description) {}

    public record Dimension(String column, Set<String> joins, String description) {}

    public record Join(String clause, Set<String> requires) {}

    public static final Map<String, Metric> METRICS;
    public static final Map<String, Dimension> DIMENSIONS;
    public static final Map<String, Join> JOINS;
    public static final Map<String, String> GRAINS;

    static {
        Map<String, Metric> metrics = new LinkedHashMap<>();
        metrics.put("revenue", new Metric(
                "SUM(oi.quantity * oi.unit_price)", Set.of("order_items"), Set.of(),
                "Total sales amount (quantity x unit price)"));
        metrics.put("order_count", new Metric(
                "COUNT(DISTINCT o.id)", Set.of(), Set.of(),
                "Number of distinct orders"));
        metrics.put("quantity", new Metric(
                "SUM(oi.quantity)", Set.of("order_items"), Set.of(),
                "Total units sold"));
        metrics.put("average_order_value", new Metric(
                "SUM(oi.quantity * oi.unit_price) / COUNT(DISTINCT o.id)", Set.of("order_items"),
                Set.of("product", "category"),
                "Revenue per order. Cannot be grouped by product or category"));
        METRICS = Collections.unmodifiableMap(metrics);

        Map<String, Dimension> dimensions = new LinkedHashMap<>();
        dimensions.put("product", new Dimension(
                "p.name", Set.of("products"), "Product name"));
        dimensions.put("category", new Dimension(
                "p.category", Set.of("products"), "Product category. Values: Electronics, Clothing, Home, Sports"));
        dimensions.put("region", new Dimension(
                "c.region", Set.of("customers"), "Customer region. Values: Western, Eastern, Northern, Southern"));
        dimensions.put("customer", new Dimension(
                "c.name", Set.of("customers"), "Customer name"));
        dimensions.put("order_date", new Dimension(
                "o.order_date", Set.of(), "Order date. Optional grain: day, month, year"));
        DIMENSIONS = Collections.unmodifiableMap(dimensions);

        Map<String, Join> joins = new LinkedHashMap<>();
        joins.put("customers", new Join("JOIN customers c ON c.id = o.customer_id", Set.of()));
        joins.put("order_items", new Join("JOIN order_items oi ON oi.order_id = o.id", Set.of()));
        joins.put("products", new Join("JOIN products p ON p.id = oi.product_id", Set.of("order_items")));
        JOINS = Collections.unmodifiableMap(joins);

        Map<String, String> grains = new LinkedHashMap<>();
        grains.put("day", "DATE(%s)");
        grains.put("month", "DATE_FORMAT(%s, '%%Y-%%m-01')");
        grains.put("year", "DATE_FORMAT(%s, '%%Y-01-01')");
        GRAINS = Collections.unmodifiableMap(grains);
    }

    private SemanticLayer() {}

    public static String describeMetrics() {
        StringBuilder sb = new StringBuilder();
        METRICS.forEach((name, m) -> sb.append("- ").append(name).append(": ").append(m.description()).append("\n"));
        return sb.toString();
    }

    public static String describeDimensions() {
        StringBuilder sb = new StringBuilder();
        DIMENSIONS.forEach((name, d) -> sb.append("- ").append(name).append(": ").append(d.description()).append("\n"));
        return sb.toString();
    }
}
