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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer;

@DisplayName("comment removal")
class CodeSanitizerServiceTest {

    private final CodeAnalyzer analyzer = mock(CodeAnalyzer.class);
    private final CodeSanitizerService sanitizer = new CodeSanitizerService(analyzer);

    @Test
    @DisplayName("uses the syntax-tree result when the analyzer can parse the language")
    void prefersAnalyzer() {
        when(analyzer.removeComments("x = 1  # c", "python")).thenReturn(Optional.of("x = 1"));

        assertEquals("x = 1", sanitizer.removeComments("x = 1  # c", "python"));
    }

    @Test
    @DisplayName("falls back to the regular expression when the analyzer cannot")
    void fallsBackToRegex() {
        when(analyzer.removeComments("int a; // c", "java")).thenReturn(Optional.empty());

        assertEquals("int a;", sanitizer.removeComments("int a; // c", "java"));
    }

    @Test
    @DisplayName("scaffolding removal uses the analyzer's result, or leaves the code as it is")
    void scaffolding() {
        when(analyzer.removeScaffolding("class A { void f() {} }", "java")).thenReturn(Optional.of("void f() {}"));
        when(analyzer.removeScaffolding("void g() {}", "java")).thenReturn(Optional.empty());

        assertEquals("void f() {}", sanitizer.removeScaffolding("class A { void f() {} }", "java"));
        assertEquals("void g() {}", sanitizer.removeScaffolding("void g() {}", "java"));
    }

    @Test
    @DisplayName("null code becomes empty")
    void nullCode() {
        assertEquals("", sanitizer.removeComments(null, "python"));
    }

    @Test
    @DisplayName("code in a Markdown fence is taken out of it, with text before and after the fence dropped")
    void unwrapsMarkdownFence() {
        assertEquals(
            "def f(x):\n    return x",
            CodeSanitizerService.unwrapMarkdownFence("```python\ndef f(x):\n    return x\n```")
        );
        assertEquals(
            "int f() {\n    return 1;\n}",
            CodeSanitizerService.unwrapMarkdownFence(
                "Here is the code:\n\n  ```java\nint f() {\n    return 1;\n}\n  ```\n\nUsage:\n```\nf();\n```"
            )
        );
        assertEquals("x = 1", CodeSanitizerService.unwrapMarkdownFence("```\nx = 1"));
    }

    @Test
    @DisplayName("code without a line holding only a fence is left as it is, even with backticks in it")
    void leavesCodeWithoutFence() {
        String code = "def f():\n    return \"```python\"\n";
        assertEquals(code, CodeSanitizerService.unwrapMarkdownFence(code));
        String inline = "```python x = 1";
        assertEquals(inline, CodeSanitizerService.unwrapMarkdownFence(inline));
    }
}
