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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.uni_passau.fim.se2.domain.interfaces.AiModelClient;

@DisplayName("ContextAnnotator separating context from the puzzle")
class ContextAnnotatorTest {

    /** 25 lines: a three-line header, 19 body lines (line 13 among them), a three-line footer. */
    private static final List<String> LINES = program();

    private static List<String> program() {
        List<String> lines = new ArrayList<>(List.of("import java.util.List;", "class A {", "  void f() {"));
        IntStream.rangeClosed(4, 22).forEach(i -> lines.add("    x" + i + " = " + i + ";"));
        lines.addAll(List.of("  }", "  public static void main(String[] a) { new A().f(); }", "}"));
        return lines;
    }

    private static ContextAnnotator.ContextResult annotate(List<Integer> contextLines) throws Exception {
        AiModelClient client = mock(AiModelClient.class);
        when(client.generateJson(anyString(), anyString(), anyString(), any()))
            .thenReturn("{\"contextLines\":" + contextLines + "}");
        return new ContextAnnotator(client).annotate(String.join("\n", LINES), "java");
    }

    private static String lines(int from, int to) {
        return IntStream.rangeClosed(from, to).mapToObj(i -> LINES.get(i - 1)).collect(Collectors.joining("\n"));
    }

    @Test
    @DisplayName("a line marked as context inside the puzzle stays in its place instead of moving to the end")
    void contextInsidePuzzleStaysInPlace() throws Exception {
        ContextAnnotator.ContextResult result = annotate(List.of(1, 2, 3, 13, 23, 24, 25));

        assertEquals(lines(1, 3), result.contextBefore());
        assertEquals(lines(4, 22), result.puzzleSolution());
        assertEquals(lines(23, 25), result.contextAfter());
    }

    @Test
    @DisplayName("an answer marking every line as context leaves the solution as it is")
    void allContextIsIgnored() throws Exception {
        ContextAnnotator.ContextResult result = annotate(
            IntStream.rangeClosed(1, 25).boxed().toList()
        );

        assertEquals(String.join("\n", LINES), result.puzzleSolution());
        assertNull(result.contextBefore());
        assertNull(result.contextAfter());
    }
}
