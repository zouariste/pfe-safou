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
package de.uni_passau.fim.se2.infrastructure.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.uni_passau.fim.se2.application.service.CodeSanitizerService;
import de.uni_passau.fim.se2.domain.entities.SolveContract;
import de.uni_passau.fim.se2.infrastructure.ai.AiProps;
import de.uni_passau.fim.se2.infrastructure.ai.AiTransport;
import de.uni_passau.fim.se2.infrastructure.ai.IdentityLoader;
import de.uni_passau.fim.se2.infrastructure.ai.PromptBuilder;

/**
 * What the solver calls do with the code the model returns.
 */
@DisplayName("OpenAiClient solver answers")
class OpenAiClientSolveTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CodeSanitizerService sanitizer = mock(CodeSanitizerService.class);

    @Test
    @DisplayName("the simple solver's code comes back without comments and program scaffolding")
    void simpleSolveRemovesScaffolding() throws Exception {
        String program = "def f(x):\n    return x  # identity\n\nprint(f(1))";
        when(sanitizer.removeComments(program, "python")).thenReturn("def f(x):\n    return x\n\nprint(f(1))");
        when(sanitizer.removeScaffolding(eq("def f(x):\n    return x\n\nprint(f(1))"), eq("python")))
            .thenReturn("def f(x):\n    return x");

        String code = clientAnswering(program).solveProblemSimple("Return x", "python").asText();

        assertEquals("def f(x):\n    return x", code);
    }

    @Test
    @DisplayName("a solution wrapped in a Markdown fence reaches comment removal without the fence")
    void solveUnwrapsMarkdownFence() throws Exception {
        when(sanitizer.removeComments(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        SolveContract contract = SolveContract.builder()
            .language("python")
            .taskStatement("Return x")
            .functionSignature("def f(x):")
            .purposeBehavior("Returns x.")
            .prePostConditions("none")
            .example(new SolveContract.ExamplePair("f(1)", "1"))
            .build();

        String code = clientAnswering("```python\ndef f(x):\n    return x\n```").solveProblem(contract).asText();

        assertEquals("def f(x):\n    return x", code);
    }

    /** A client whose every request is answered with {@code {"code": <code>}}. */
    @SuppressWarnings("unchecked")
    OpenAiClient clientAnswering(String code) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.putArray("choices").addObject().putObject("message")
            .put("content", MAPPER.createObjectNode().put("code", code).toString());
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body.toString());
        AiTransport transport = mock(AiTransport.class);
        when(transport.sendRequest(any())).thenReturn(response);

        PromptBuilder promptBuilder = new PromptBuilder(MAPPER, new IdentityLoader("classpath:unused/"));
        return new OpenAiClient("test-key", transport, promptBuilder, new AiProps(), null, sanitizer);
    }
}
