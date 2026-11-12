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

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Comment removal by syntax tree. Needs the native libraries, like {@link TreeSitterCodeAnalyzerTest}; build them with
 * {@code scripts/build-treesitter-natives.sh}.
 */
@EnabledIf("nativesAvailable")
@DisplayName("comment removal with tree-sitter")
class CommentStripperTest {

    private final TreeSitterCodeAnalyzer analyzer = new TreeSitterCodeAnalyzer();

    static boolean nativesAvailable() {
        return TreeSitterNatives.isAvailable();
    }

    @Test
    @DisplayName("Python: comments and docstrings go, # inside strings stays, blank lines stay")
    void python() {
        String code = """
            def f(x):
                \"\"\"Return a colour.\"\"\"
                # a whole-line comment
                color = "#ff0000"  # inline
                label = "Step #1"

                return color + label + "ä"  # ü
            """;

        assertEquals(
            """
                def f(x):
                    color = "#ff0000"
                    label = "Step #1"

                    return color + label + "ä"                """,
            strip(code, "python")
        );
    }

    @Test
    @DisplayName("Python: a docstring that is the whole body stays, so the body is not left empty")
    void pythonDocstringOnlyBody() {
        String code = "def g():\n    \"\"\"Only a docstring.\"\"\"";

        assertEquals(code, strip(code, "python"));
    }

    @Test
    @DisplayName("Java: line and block comments go, // inside strings stays")
    void java() {
        String code = """
            class A {
                // a line comment
                /* a block
                   comment */
                String url = "http://example.org"; // tail
                int a = 1; /* mid */ int b = 2;
            }
            """;

        assertEquals(
            """
                class A {
                    String url = "http://example.org";
                    int a = 1;  int b = 2;
                }                """,
            strip(code, "java")
        );
    }

    @Test
    @DisplayName("languages tree-sitter is not set up for are left to the caller")
    void unsupportedLanguage() {
        assertTrue(analyzer.removeComments("SELECT 1 -- x", "sql").isEmpty());
    }

    private String strip(String code, String language) {
        Optional<String> result = analyzer.removeComments(code, language);
        assertTrue(result.isPresent());
        return result.get();
    }
}
