package com.chat.agent.query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.chat.agent.dto.DescriptiveQuery;
import com.chat.agent.dto.QueryFilter;
import com.chat.agent.repository.AnalyticsRepo;
import com.chat.agent.semantic.SemanticField;
import com.chat.agent.semantic.SemanticModel;

/**
 * Matches what the person typed against the values that actually exist, the way a search box
 * would. Runs before any SQL is built, so the query stays an exact match and can use an index.
 *
 * One clear winner is used and reported. Anything less certain is handed back to the person
 * as a question rather than guessed at.
 */
@Component
public class ValueResolver {

    private static final Logger log = LoggerFactory.getLogger(ValueResolver.class);

    /** above this many distinct values a dimension is not worth resolving in memory */
    private static final int MAX_VALUES = 5000;
    private static final int SUGGESTIONS = 5;

    /** a match must be at least this good, and this far ahead of the runner up, to be used */
    private static final double ACCEPT_SCORE = 0.82;
    private static final double CLEAR_GAP = 0.08;

    /** worth showing as a suggestion, but never used without asking */
    private static final double SUGGEST_SCORE = 0.45;

    private final AnalyticsRepo repo;
    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();

    public ValueResolver(AnalyticsRepo repo) {
        this.repo = repo;
    }

    /** Rewrites loose filter values to real ones, noting any substitution it made. */
    public void resolve(DescriptiveQuery query, SemanticModel model, List<String> notes) {
        for (QueryFilter filter : query.getFilters()) {
            if (!"=".equals(filter.getOperator()) || !(filter.getValue() instanceof String typed)) {
                continue;
            }

            SemanticField field = model.getDimensions().get(filter.getField());
            if (field == null || !field.isResolve()) {
                continue;
            }

            List<String> values = valuesFor(model, filter.getField(), field);
            if (values.isEmpty()) {
                continue;
            }

            List<String> ranked = rank(typed, values, SUGGEST_SCORE);
            double best = ranked.isEmpty() ? 0 : score(typed, ranked.get(0));
            double second = ranked.size() > 1 ? score(typed, ranked.get(1)) : 0;

            if (best >= 0.999) {
                continue; // already exactly right
            }

            if (best >= ACCEPT_SCORE && best - second >= CLEAR_GAP) {
                filter.setValue(ranked.get(0));
                notes.add("The question said \"" + typed + "\"; the matching " + filter.getField()
                        + " is \"" + ranked.get(0) + "\".");
                continue;
            }

            throw ambiguous(model, filter.getField(), typed, ranked);
        }
    }

    /**
     * Builds the question we put back to the person. If the value does not suit the field the
     * model chose, say which field it does suit — a product name asked for as a vendor is the
     * usual case, and silently matching the brand would answer a different question entirely.
     */
    private AmbiguousValueException ambiguous(SemanticModel model, String fieldName, String typed,
                                              List<String> ranked) {
        double inField = ranked.isEmpty() ? 0 : score(typed, ranked.get(0));

        // the value may simply belong to a different dimension: a product name asked for as a
        // vendor is the usual case, and matching the brand would answer a different question
        String otherField = null;
        List<String> otherValues = List.of();
        double otherScore = SUGGEST_SCORE;

        for (Map.Entry<String, SemanticField> entry : model.getDimensions().entrySet()) {
            if (entry.getKey().equals(fieldName) || !entry.getValue().isResolve()) {
                continue;
            }
            List<String> elsewhere = rank(typed, valuesFor(model, entry.getKey(), entry.getValue()), SUGGEST_SCORE);
            if (elsewhere.isEmpty()) {
                continue;
            }
            double best = score(typed, elsewhere.get(0));
            if (best > otherScore) {
                otherScore = best;
                otherField = entry.getKey();
                otherValues = elsewhere.subList(0, Math.min(SUGGESTIONS, elsewhere.size()));
            }
        }

        // show whichever side actually fits what was typed, never both
        if (otherField != null && otherScore > inField) {
            return new AmbiguousValueException(fieldName, typed, List.of(), otherField, shortlist(typed, otherValues));
        }
        return new AmbiguousValueException(fieldName, typed, shortlist(typed, ranked), null, List.of());
    }

    /** One exact hit needs no company; anything less shows a few options. */
    private List<String> shortlist(String typed, List<String> ranked) {
        if (!ranked.isEmpty() && score(typed, ranked.get(0)) >= 0.999) {
            return List.of(ranked.get(0));
        }
        return ranked.subList(0, Math.min(SUGGESTIONS, ranked.size()));
    }

    private List<String> rank(String typed, List<String> values, double floor) {
        List<String> ranked = new ArrayList<>();
        for (String value : values) {
            if (score(typed, value) >= floor) {
                ranked.add(value);
            }
        }
        ranked.sort(Comparator.comparingDouble((String value) -> score(typed, value)).reversed());
        return ranked;
    }

    /**
     * Word overlap handles shortened names, trigrams handle misspellings.
     *
     * The overlap is deliberately asymmetric. Typing fewer words than the real value is normal
     * ("Cosmogear Max" for "Hundred Cosmogear Max"). Typing more words than the value explains
     * is a warning sign: "Hundred Cosmogear Max" should not match the vendor "Hundred", because
     * two thirds of what was typed would be thrown away.
     */
    private double score(String typed, String candidate) {
        String a = normalise(typed);
        String b = normalise(candidate);
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        if (a.equals(b)) {
            return 1.0;
        }

        Set<String> typedWords = words(a);
        Set<String> candidateWords = words(b);

        int shared = 0;
        for (String word : typedWords) {
            if (candidateWords.contains(word)) {
                shared++;
            }
        }

        // how much of what the person typed this value accounts for, and how complete the value is
        double explained = (double) shared / typedWords.size();
        double covered = (double) shared / candidateWords.size();
        double overlap = 0.95 * explained * (0.7 + 0.3 * covered);

        return Math.max(overlap, dice(trigrams(a), trigrams(b)));
    }

    private String normalise(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    private Set<String> words(String normalised) {
        return new LinkedHashSet<>(List.of(normalised.split(" ")));
    }

    private Set<String> trigrams(String text) {
        String padded = "  " + text + "  ";
        Set<String> result = new HashSet<>();
        for (int i = 0; i + 3 <= padded.length(); i++) {
            result.add(padded.substring(i, i + 3));
        }
        return result;
    }

    private double dice(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int shared = 0;
        for (String gram : a) {
            if (b.contains(gram)) {
                shared++;
            }
        }
        return 2.0 * shared / (a.size() + b.size());
    }

    /** Loaded once per dimension and kept; restart the app to pick up new values. */
    private List<String> valuesFor(SemanticModel model, String name, SemanticField field) {
        return cache.computeIfAbsent(model.getDataset() + "." + name, key -> load(model, key, field));
    }

    private List<String> load(SemanticModel model, String key, SemanticField field) {
        String expression = field.getSql();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT ").append(expression)
                .append(" FROM ").append(model.getBaseTable());
        for (String join : model.joinsFor(List.of(expression))) {
            sql.append(" ").append(join);
        }
        sql.append(" WHERE ").append(expression).append(" IS NOT NULL")
                .append(" LIMIT ").append(MAX_VALUES + 1);

        List<String> values = repo.distinctValues(sql.toString());
        if (values.size() > MAX_VALUES) {
            log.info("not resolving {}: more than {} distinct values", key, MAX_VALUES);
            return List.of();
        }
        log.info("loaded {} values for {}", values.size(), key);
        return values;
    }
}
