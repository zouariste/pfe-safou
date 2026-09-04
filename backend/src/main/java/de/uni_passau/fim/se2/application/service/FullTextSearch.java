/*
 * Copyright (C) 2026 CodeOwl contributors
 *
 * This file is part of CodeOwl.
 *
 * CodeOwl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * CodeOwl is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CodeOwl. If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_passau.fim.se2.application.service;

import java.util.List;
import java.util.Map;

/**
 * The shape of a free-text query, shared by everything in the product that has a search box.
 *
 * <p>Lifted out of {@link SequenceSearchService}, unchanged, when the worksheet marketplace
 * gained a second one. The two searches look at different tables but must answer the same way:
 * a teacher who types "Prüfung" in one box and "prue" in the other has no reason to expect
 * different rules about umlauts or half-typed words, and two copies of this logic would have
 * drifted apart the first time somebody fixed one of them.
 *
 * <p>{@link SequenceSearchService} keeps thin delegates for the two members its own test pins
 * down, so that test still describes the behaviour where it was written.
 */
final class FullTextSearch {

    private FullTextSearch() {
    }

    /**
     * SQL fragment for a free-text tsquery that ORs together four ways a search term can match,
     * combined with the tsquery {@code ||} (OR) operator. Every occurrence takes four bind
     * parameters, filled in by {@link #addTsqueryParams}, in this order:
     *
     * <ol>
     *   <li>{@code websearch_to_tsquery(query)} — the query as typed, with real operators
     *       ("exact phrase", {@code -exclude}, {@code or}).</li>
     *   <li>{@code websearch_to_tsquery(foldedQuery)} — see {@link #foldGermanUmlauts}: rows
     *       written before the semantic-tagger prompt bug was fixed spell umlauts as
     *       "ae"/"oe"/"ue"/"ss" digraphs. Folding the query the same way lets a search typed
     *       with proper umlauts still find that older data, without rewriting the stored text
     *       or rebuilding the index.</li>
     *   <li>a prefix match on every word of {@code query} (see {@link #prefixTsquerySubexpr()})
     *       — the search boxes are live and debounced, so most keystrokes submit a word that is
     *       still being typed. {@code websearch_to_tsquery} matches whole words only, so without
     *       this branch search-as-you-type shows nothing until the last word is finished. It
     *       loses websearch's operators, which is why it is additive rather than a
     *       replacement.</li>
     *   <li>the same prefix match on {@code foldedQuery} — typing "prü" while an old row holds
     *       "prue" needs the fold and the prefix at once.</li>
     * </ol>
     */
    static final String TSQUERY_EXPR = """
            (websearch_to_tsquery('simple', ?) || websearch_to_tsquery('simple', ?) \
            || %1$s || %1$s)\
            """.formatted(prefixTsquerySubexpr());

    /**
     * Builds an all-words-as-prefixes tsquery from arbitrary raw text — safe against tsquery
     * syntax errors (and SQL injection, since the text only ever flows through as a bind
     * parameter) because {@code to_tsvector} does the tokenizing: its output lexemes can never
     * contain tsquery metacharacters ({@code & | ! ( ) :}), so joining them back together with
     * {@code ':*'} suffixes and re-parsing with {@code to_tsquery} is always well-formed. Falls
     * back to an empty (never-matching) tsquery for blank input rather than erroring.
     */
    private static String prefixTsquerySubexpr() {
        return """
                COALESCE(
                    (SELECT to_tsquery('simple', string_agg(lexeme || ':*', ' & '))
                       FROM unnest(to_tsvector('simple', ?)) AS u(lexeme, positions, weights)),
                    ''::tsquery
                )""";
    }

    /**
     * Adds the four bind parameters one {@link #TSQUERY_EXPR} occurrence needs, in order.
     *
     * <p>The SQL placeholders are position-based, so a reorder here silently breaks every query
     * using the expression, with no compiler error to catch it. That is what
     * {@code SequenceSearchServiceTest} pins down.
     */
    static void addTsqueryParams(List<Object> params, String query) {
        String folded = foldGermanUmlauts(query);
        params.add(query);
        params.add(folded);
        params.add(query);
        params.add(folded);
    }

    /** ä/ö/ü/ß → ae/oe/ue/ss (both cases); every other character is left untouched. */
    private static final Map<Character, String> UMLAUT_FOLD = Map.of(
            'ä', "ae", 'ö', "oe", 'ü', "ue",
            'Ä', "Ae", 'Ö', "Oe", 'Ü', "Ue",
            'ß', "ss"
    );

    static String foldGermanUmlauts(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String folded = UMLAUT_FOLD.get(c);
            sb.append(folded != null ? folded : String.valueOf(c));
        }
        return sb.toString();
    }

    /** Clamps a requested page size into what the server is willing to serve. */
    static int clampPageSize(int requested, int fallback, int max) {
        int size = requested <= 0 ? fallback : requested;
        return Math.min(size, max);
    }
}
