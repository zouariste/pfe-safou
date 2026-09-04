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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure-logic pieces of the umlaut-tolerant, prefix-matching free-text search: umlaut folding
 * and the fixed parameter order every {@link SequenceSearchService#TSQUERY_EXPR} occurrence
 * relies on.
 *
 * <p>What this class deliberately does NOT cover: whether the SQL itself actually matches rows in
 * Postgres (the real behaviour behind both the umlaut-fold and search-as-you-type fixes). The
 * project has no database-integration test setup (no Testcontainers, no live-DB test profile —
 * every existing test here is a Mockito unit test), so that part was verified by hand against a
 * running dev database rather than in this suite. A regression in the SQL text itself (e.g. a
 * broken tsquery expression) would not be caught by these tests.
 */
class SequenceSearchServiceTest {

    @Nested
    @DisplayName("foldGermanUmlauts")
    class FoldGermanUmlauts {

        @Test
        @DisplayName("folds each umlaut and ß to its digraph spelling")
        void foldsEachUmlaut() {
            assertEquals("ae", SequenceSearchService.foldGermanUmlauts("ä"));
            assertEquals("oe", SequenceSearchService.foldGermanUmlauts("ö"));
            assertEquals("ue", SequenceSearchService.foldGermanUmlauts("ü"));
            assertEquals("ss", SequenceSearchService.foldGermanUmlauts("ß"));
        }

        @Test
        @DisplayName("folds uppercase umlauts too")
        void foldsUppercaseUmlauts() {
            assertEquals("Ae", SequenceSearchService.foldGermanUmlauts("Ä"));
            assertEquals("Oe", SequenceSearchService.foldGermanUmlauts("Ö"));
            assertEquals("Ue", SequenceSearchService.foldGermanUmlauts("Ü"));
        }

        @Test
        @DisplayName("folds a real word exactly the way the pre-fix semantic tagger wrote it")
        void foldsRealWord() {
            // "Prüfung" is the exact case the umlaut-search fix targets: sequences tagged before
            // the semantic-tagger prompt was fixed wrote "Pruefung" for this same word.
            assertEquals("Pruefung", SequenceSearchService.foldGermanUmlauts("Prüfung"));
            assertEquals("Strasse", SequenceSearchService.foldGermanUmlauts("Straße"));
            assertEquals("Groessenabfrage", SequenceSearchService.foldGermanUmlauts("Größenabfrage"));
        }

        @Test
        @DisplayName("leaves text with no umlauts or ß untouched")
        void leavesPlainTextUntouched() {
            String plain = "Null pr Collection-Objekten 123";
            assertEquals(plain, SequenceSearchService.foldGermanUmlauts(plain));
        }

        @Test
        @DisplayName("folds every occurrence, not just the first")
        void foldsEveryOccurrence() {
            assertEquals("Groesse und Groesse", SequenceSearchService.foldGermanUmlauts("Größe und Größe"));
        }

        @Test
        @DisplayName("empty string folds to itself")
        void emptyStringFoldsToItself() {
            assertEquals("", SequenceSearchService.foldGermanUmlauts(""));
        }
    }

    @Nested
    @DisplayName("addTsqueryParams")
    class AddTsqueryParams {

        @Test
        @DisplayName("adds exactly the four params TSQUERY_EXPR's four placeholders need, in order")
        void addsFourParamsInOrder() {
            List<Object> params = new ArrayList<>();
            SequenceSearchService.addTsqueryParams(params, "Prüfung");

            // Order matters: it must line up 1:1 with TSQUERY_EXPR's four '?' placeholders
            // (exact query, folded query, exact-as-prefix, folded-as-prefix). A reorder here
            // silently binds the wrong value to the wrong branch with no compiler error.
            assertEquals(List.of("Prüfung", "Pruefung", "Prüfung", "Pruefung"), params);
        }

        @Test
        @DisplayName("appends to whatever the params list already contains, rather than replacing it")
        void appendsRatherThanReplaces() {
            List<Object> params = new ArrayList<>();
            params.add("existing-param");
            SequenceSearchService.addTsqueryParams(params, "test");

            assertEquals(List.of("existing-param", "test", "test", "test", "test"), params);
        }

        @Test
        @DisplayName("still adds four params for a query with no umlauts (folded form equals the original)")
        void plainQueryStillAddsFourParams() {
            List<Object> params = new ArrayList<>();
            SequenceSearchService.addTsqueryParams(params, "recursion");

            assertEquals(List.of("recursion", "recursion", "recursion", "recursion"), params);
        }
    }

    @Nested
    @DisplayName("TSQUERY_EXPR")
    class TsqueryExprShape {

        @Test
        @DisplayName("has exactly four '?' placeholders, matching addTsqueryParams' four params")
        void hasExactlyFourPlaceholders() {
            // The single most likely way to silently break every FTS query at once: adding or
            // removing a branch in TSQUERY_EXPR without updating addTsqueryParams' four adds (or
            // vice versa) desyncs every bind-parameter position from here on in buildWhere,
            // fetchPage's ts_headline, and both ORDER BY relevance branches.
            long placeholderCount = SequenceSearchService.TSQUERY_EXPR.chars().filter(c -> c == '?').count();
            assertEquals(4, placeholderCount);
        }

        @Test
        @DisplayName("is a single self-contained, parenthesised OR expression")
        void isParenthesisedOrExpression() {
            String expr = SequenceSearchService.TSQUERY_EXPR.strip();
            assertEquals('(', expr.charAt(0));
            assertEquals(')', expr.charAt(expr.length() - 1));
            assertBalancedParens(expr);
        }

        private void assertBalancedParens(String sql) {
            int depth = 0;
            for (char c : sql.toCharArray()) {
                if (c == '(') depth++;
                if (c == ')') depth--;
                assertTrue(depth >= 0, "unbalanced parentheses in TSQUERY_EXPR");
            }
            assertEquals(0, depth, "unbalanced parentheses in TSQUERY_EXPR");
        }
    }
}
