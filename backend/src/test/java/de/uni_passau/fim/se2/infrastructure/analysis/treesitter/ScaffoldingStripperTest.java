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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Reducing a generated program to its function(s). Needs the native libraries, like {@link CommentStripperTest}.
 */
@EnabledIf("nativesAvailable")
@DisplayName("program scaffolding removal with tree-sitter")
class ScaffoldingStripperTest {

    private final TreeSitterCodeAnalyzer analyzer = new TreeSitterCodeAnalyzer();

    static boolean nativesAvailable() {
        return TreeSitterNatives.isAvailable();
    }

    @Test
    @DisplayName("Java: the class and main go, imports and the other members stay, unindented")
    void javaClassWithMain() {
        String code = """
            import java.util.ArrayList;
            import java.util.List;

            public class Solution {
                public static List<Integer> evensOf(List<Integer> numbers) {
                    List<Integer> evens = new ArrayList<>();
                    for (Integer n : numbers) {
                        if (n % 2 == 0) {
                            evens.add(n);
                        }
                    }
                    return evens;
                }

                private static boolean isEven(int n) {
                    return n % 2 == 0;
                }

                public static void main(String[] args) {
                    System.out.println(evensOf(List.of(1, 2, 3, 4)));
                }
            }
            """;

        assertEquals(
            """
                import java.util.ArrayList;
                import java.util.List;

                public static List<Integer> evensOf(List<Integer> numbers) {
                    List<Integer> evens = new ArrayList<>();
                    for (Integer n : numbers) {
                        if (n % 2 == 0) {
                            evens.add(n);
                        }
                    }
                    return evens;
                }

                private static boolean isEven(int n) {
                    return n % 2 == 0;
                }                """,
            analyzer.removeScaffolding(code, "java").orElseThrow()
        );
    }

    @Test
    @DisplayName("Java: a bare method, or a class with only main, is left alone")
    void javaLeftAlone() {
        assertTrue(analyzer.removeScaffolding("public static int f() {\n    return 1;\n}", "java").isEmpty());
        assertTrue(
            analyzer.removeScaffolding(
                "public class Main {\n    public static void main(String[] a) {\n        int x = 1;\n    }\n}", "java"
            ).isEmpty()
        );
    }

    @Test
    @DisplayName("Python: example calls and the main guard go, imports and functions stay")
    void pythonTopLevelCode() {
        String code = """
            import math


            def root_of_mean(numbers):
                if not numbers:
                    return 0.0
                return math.sqrt(sum(numbers) / len(numbers))


            print(root_of_mean([4.0, 16.0]))

            if __name__ == "__main__":
                print(root_of_mean([1.0]))
            """;

        assertEquals(
            """
                import math


                def root_of_mean(numbers):
                    if not numbers:
                        return 0.0
                    return math.sqrt(sum(numbers) / len(numbers))                """,
            analyzer.removeScaffolding(code, "python").orElseThrow()
        );
    }

    @Test
    @DisplayName("Python: a plain function is left alone")
    void pythonLeftAlone() {
        assertTrue(analyzer.removeScaffolding("def f(x):\n    return x + 1", "python").isEmpty());
    }
}
