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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.uni_passau.fim.se2.application.service.CodeSanitizerService;
import de.uni_passau.fim.se2.infrastructure.ai.AiProps;
import de.uni_passau.fim.se2.infrastructure.ai.AiTransport;
import de.uni_passau.fim.se2.infrastructure.ai.IdentityLoader;
import de.uni_passau.fim.se2.infrastructure.ai.PromptBuilder;

/**
 * Reading the answer out of a chat-completions response, as OpenAI sends it and as other OpenAI-compatible servers may.
 */
@DisplayName("OpenAiClient reading responses")
class OpenAiClientResponseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OpenAiClient client = new OpenAiClient(
        "test-key", mock(AiTransport.class),
        new PromptBuilder(MAPPER, new IdentityLoader("classpath:unused/")),
        new AiProps(), null, mock(CodeSanitizerService.class)
    );

    /** A response whose only choice has the given message and finish reason. */
    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(ObjectNode message, String finishReason) {
        ObjectNode body = MAPPER.createObjectNode();
        ObjectNode choice = body.putArray("choices").addObject();
        choice.set("message", message);
        choice.put("finish_reason", finishReason);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.body()).thenReturn(body.toString());
        return response;
    }

    private static ObjectNode content(String content) {
        return MAPPER.createObjectNode().put("role", "assistant").put("content", content);
    }

    @Test
    @DisplayName("an OpenAI answer is read as before")
    void plainAnswer() throws Exception {
        assertEquals(
            MAPPER.readTree("{\"code\":\"x = 1\"}"),
            client.parseResponse(response(content("{\"code\":\"x = 1\"}"), "stop"))
        );
    }

    @Test
    @DisplayName("a think block before the answer and a fence around it are removed")
    void thinkBlockAndFence() throws Exception {
        String answer = "<think>\nThe user wants {\"code\": ...}\n</think>\n\n```json\n{\"code\":\"```py\"}\n```";

        assertEquals(
            MAPPER.readTree("{\"code\":\"```py\"}"), client.parseResponse(response(content(answer), "stop"))
        );
    }

    @Test
    @DisplayName("an answer cut off at the token limit says so instead of failing to parse")
    void cutOff() {
        ObjectNode message = content("{\"tasks\": [{\"tier\": 1, \"stat").put("reasoning_content", "Let me think");

        IOException e = assertThrows(IOException.class, () -> client.parseResponse(response(message, "length")));

        assertTrue(e.getMessage().contains("cut off at its token limit after reasoning"), e.getMessage());
    }

    @Test
    @DisplayName("an empty answer and a refusal are reported as such")
    void emptyAndRefused() {
        ObjectNode empty = MAPPER.createObjectNode().put("role", "assistant").putNull("content");
        IOException e = assertThrows(IOException.class, () -> client.parseResponse(response(empty, "stop")));
        assertTrue(e.getMessage().contains("empty (finish_reason=stop)"), e.getMessage());

        ObjectNode refused = content("").put("refusal", "I can't help with that.");
        e = assertThrows(IOException.class, () -> client.parseResponse(response(refused, "stop")));
        assertTrue(e.getMessage().contains("refused"), e.getMessage());
    }
}
