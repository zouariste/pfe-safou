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
package de.uni_passau.fim.se2.infrastructure.analysis.treesitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer.AstAnalysisResult;
import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer.ComplexityResult;

/**
 * The node-type names in {@link LanguageRegistry} are tied to the grammar versions pinned in
 * {@code scripts/build-treesitter-natives.sh}; a grammar update that renames a node silently zeroes a metric. Each
 * snippet below has one function with a call inside an {@code if} inside a loop, so every metric must come out
 * non-trivial for every language.
 *
 * <p>
 * The grammar tests need the native libraries and are skipped without them, like the database tests without Docker:
 * build them with {@code scripts/build-treesitter-natives.sh}.
 */
class TreeSitterCodeAnalyzerTest {

    static boolean nativesAvailable() {
        return TreeSitterNatives.isAvailable();
    }

    static Stream<Arguments> snippets() {
        return Stream.of(
            Arguments.of("java", """
                class A {
                    void run(int[] xs) {
                        for (int x : xs) {
                            if (x > 0) { System.out.println(x); }
                        }
                    }
                }
                """),
            Arguments.of("python", """
                def run(xs):
                    for x in xs:
                        if x > 0:
                            print(x)
                """),
            Arguments.of("javascript", """
                function run(xs) {
                    for (let i = 0; i < xs.length; i++) {
                        if (xs[i] > 0) { console.log(xs[i]); }
                    }
                }
                """),
            Arguments.of("typescript", """
                function run(xs: number[]): void {
                    for (let i = 0; i < xs.length; i++) {
                        if (xs[i] > 0) { console.log(xs[i]); }
                    }
                }
                """),
            Arguments.of("c", """
                void run(int *xs, int n) {
                    for (int i = 0; i < n; i++) {
                        if (xs[i] > 0) { printf("%d", xs[i]); }
                    }
                }
                """),
            Arguments.of("cpp", """
                void run(std::vector<int> xs) {
                    for (int x : xs) {
                        if (x > 0) { print(x); }
                    }
                }
                """),
            Arguments.of("csharp", """
                class A {
                    void Run(int[] xs) {
                        foreach (var x in xs) {
                            if (x > 0) { Console.WriteLine(x); }
                        }
                    }
                }
                """),
            Arguments.of("rust", """
                fn run(xs: Vec<i32>) {
                    for x in xs {
                        if x > 0 { show(x); }
                    }
                }
                """),
            Arguments.of("go", """
                package main

                func run(xs []int) {
                    for _, x := range xs {
                        if x > 0 { fmt.Println(x) }
                    }
                }
                """)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("snippets")
    @EnabledIf("nativesAvailable")
    @DisplayName("every supported grammar loads and yields loop, branch, nesting and call metrics")
    void computesComplexityForEveryLanguage(String language, String code) {
        TreeSitterCodeAnalyzer analyzer = new TreeSitterCodeAnalyzer();
        assertTrue(LanguageRegistry.resolve(language).isPresent(), "grammar did not load");

        ComplexityResult result = analyzer.computeComplexity(code, language);

        assertEquals(3, result.cyclomaticComplexity(), "1 + loop + if");
        assertTrue(result.maxNestingDepth() >= 2, "if nested in loop, got " + result.maxNestingDepth());
        assertTrue(result.methodCallCount() >= 1, "call expression not recognised");
        assertTrue(result.distinctIdentifierCount() >= 1, "identifiers not recognised");
    }

    @Test
    @EnabledIf("nativesAvailable")
    @DisplayName("change analysis reports an added loop")
    void detectsAddedLoop() {
        AstAnalysisResult result = new TreeSitterCodeAnalyzer().analyzeChanges(
            "def f(x):\n    return x\n",
            "def f(xs):\n    for x in xs:\n        print(x)\n",
            "python"
        );

        assertTrue(result.changeTypes().contains("ADD_LOOPS"), result.changeTypes().toString());
    }

    @Test
    @DisplayName("without the native libraries complexity is zero and change analysis is line-based")
    void fallsBackWithoutNatives() {
        TreeSitterCodeAnalyzer analyzer = new TreeSitterCodeAnalyzer(false);

        assertEquals(ComplexityResult.zero(), analyzer.computeComplexity("int x = 1;", "java"));
        // The heuristic only compares line counts: 2 → 4 lines is one modification out of two.
        AstAnalysisResult changes = analyzer.analyzeChanges("a\nb\n", "a\nb\nc\nd\n", "java");
        assertEquals(List.of("MODIFY_CONTENT"), changes.changeTypes());
        assertEquals(0.5, changes.structuralScore(), 1e-9);
        assertTrue(analyzer.supportsLanguage("Java"));
    }
}
