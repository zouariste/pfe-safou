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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.uni_passau.fim.se2.domain.interfaces.AiModelClient;

/**
 * Both annotators ask for their own JSON shape. They used to reuse the task-description request, which forces {"task":
 * "<text>"}, so they only worked with models that put JSON inside that string.
 */
@DisplayName("annotators request their own JSON schema")
class AnnotatorStructuredResponseTest {

    private static final String FIB = String.join(
        "\n",
        "def fib(n):",
        "    if n == 0:",
        "        return 0",
        "    if n == 1:",
        "        return 1",
        "    return fib(n - 1) + fib(n - 2)"
    );

    @Test
    @DisplayName("interchangeable lines are marked from the structured answer")
    void interchangeable() throws Exception {
        AiModelClient client = mock(AiModelClient.class);
        when(
            client.generateJson(
                anyString(), anyString(), eq("interchangeable_lines"),
                eq(InterchangeableAnnotator.SCHEMA)
            )
        ).thenReturn(
            "{\"annotations\":[{\"line\":2,\"type\":\"group\",\"name\":\"base_1\"},"
                + "{\"line\":3,\"type\":\"group\",\"name\":\"base_1\"},"
                + "{\"line\":4,\"type\":\"group\",\"name\":\"base_2\"},"
                + "{\"line\":5,\"type\":\"group\",\"name\":\"base_2\"}]}"
        );

        List<String> lines = new InterchangeableAnnotator(client).annotate(FIB, "python").lines().toList();

        assertTrue(lines.get(1).contains("#group:base_1"), lines.get(1));
        assertTrue(lines.get(4).contains("#group:base_2"), lines.get(4));
        assertEquals("def fib(n):", lines.get(0));
    }

    @Test
    @DisplayName("both annotators show the code with numbered lines, so the model need not count them")
    void codeIsNumbered() throws Exception {
        AiModelClient client = mock(AiModelClient.class);
        when(client.generateJson(anyString(), anyString(), anyString(), any())).thenReturn("{}");
        String longCode = String.join("\n", Collections.nCopies(25, "x = 1"));

        new InterchangeableAnnotator(client).annotate(FIB, "python");
        new ContextAnnotator(client).annotate(longCode, "python");

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(client, times(2)).generateJson(anyString(), prompts.capture(), anyString(), any());
        String interchangeable = prompts.getAllValues().get(0);
        String context = prompts.getAllValues().get(1);
        assertTrue(interchangeable.contains("1 | def fib(n):\n2 |     if n == 0:"), interchangeable);
        assertTrue(interchangeable.contains("6 |     return fib(n - 1) + fib(n - 2)"), interchangeable);
        assertTrue(context.contains(" 1 | x = 1\n 2 | x = 1"), context);
        assertTrue(context.contains("25 | x = 1"), context);
    }

    @Test
    @DisplayName("numbering keeps blank lines, so the numbers match the lines the answer is applied to")
    void numberingCountsBlankLines() {
        assertEquals("1 | a\n2 | \n3 | b", NumberedCode.of("a\n\nb"));
    }

    @Test
    @DisplayName("the schemas require exactly the fields the annotators read")
    void schemas() {
        assertEquals("annotations", InterchangeableAnnotator.SCHEMA.path("required").path(0).asText());
        assertEquals(
            List.of("line", "type", "name"),
            InterchangeableAnnotator.SCHEMA.at("/properties/annotations/items/required").valueStream()
                .map(n -> n.asText()).toList()
        );
        assertEquals("contextLines", ContextAnnotator.SCHEMA.path("required").path(0).asText());
        assertEquals("integer", ContextAnnotator.SCHEMA.at("/properties/contextLines/items/type").asText());
    }
}
