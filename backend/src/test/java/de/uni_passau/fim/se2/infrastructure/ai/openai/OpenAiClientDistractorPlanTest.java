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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.uni_passau.fim.se2.application.service.CodeSanitizerService;
import de.uni_passau.fim.se2.domain.model.GeneratedDistractors;
import de.uni_passau.fim.se2.infrastructure.ai.AiProps;
import de.uni_passau.fim.se2.infrastructure.ai.AiTransport;
import de.uni_passau.fim.se2.infrastructure.ai.IdentityLoader;
import de.uni_passau.fim.se2.infrastructure.ai.PromptBuilder;

/**
 * Distractors generated without a plan pick one the way the automatic creator does, from the types preferred for the
 * tier, and report it so it can be stored on the task.
 */
@DisplayName("distractor generation with and without a plan")
class OpenAiClientDistractorPlanTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SOLUTION = "for i in range(n):\n    total += i";
    private static final String GIVEN_PLAN = "{\"type\":\"OFF_BY_ONE\",\"rationale\":\"loop bounds\"}";

    // The distractor request does not load an identity file.
    private final PromptBuilder promptBuilder = new PromptBuilder(MAPPER, new IdentityLoader("classpath:unused/"));

    @Test
    @DisplayName("without a plan the request offers the tier's preferred types and requires a plan back")
    void requestWithoutPlan() {
        ObjectNode request = promptBuilder.buildDistractorRequest(SOLUTION, "python", "Sum", 2, 3, null, 2);

        String userPrompt = request.path("messages").path(1).path("content").asText();
        assertTrue(userPrompt.contains("DISTRACTOR STRATEGY SELECTION"));
        assertTrue(userPrompt.contains("tier 2 of 3"));

        JsonNode schema = request.path("response_format").path("json_schema").path("schema");
        assertEquals(List.of("distractors", "plan"), texts(schema.path("required")));
        assertEquals(
            List.of("OFF_BY_ONE", "VARIABLE_MISMATCH"),
            texts(schema.path("properties").path("plan").path("properties").path("type").path("enum"))
        );
    }

    @Test
    @DisplayName("with a plan the request passes it on and asks for distractors only")
    void requestWithPlan() {
        ObjectNode request = promptBuilder.buildDistractorRequest(SOLUTION, "python", "Sum", 2, 3, GIVEN_PLAN, 2);

        String userPrompt = request.path("messages").path(1).path("content").asText();
        assertTrue(userPrompt.contains("DISTRACTOR STRATEGY:\n" + GIVEN_PLAN));
        assertFalse(userPrompt.contains("DISTRACTOR STRATEGY SELECTION"));

        JsonNode schema = request.path("response_format").path("json_schema").path("schema");
        assertEquals(List.of("distractors"), texts(schema.path("required")));
        assertTrue(schema.path("properties").path("plan").isMissingNode());
    }

    @Test
    @DisplayName("the plan the model chose comes back with the lines")
    void chosenPlanReturned() throws Exception {
        OpenAiClient client = clientAnswering(
            "{\"distractors\":[\"for i in range(n + 1):\"],"
                + "\"plan\":{\"type\":\"OFF_BY_ONE\",\"rationale\":\"bounds matter here\"}}"
        );

        GeneratedDistractors result = client.generateDistractors(SOLUTION, "python", "Sum", 2, 3, null, 1);

        assertEquals(List.of("for i in range(n + 1): #distractor"), result.lines());
        assertEquals("{\"type\":\"OFF_BY_ONE\",\"rationale\":\"bounds matter here\"}", result.plan());
    }

    @Test
    @DisplayName("a given plan is kept")
    void givenPlanKept() throws Exception {
        OpenAiClient client = clientAnswering("{\"distractors\":[\"total -= i\"]}");

        GeneratedDistractors result = client.generateDistractors(SOLUTION, "python", "Sum", 2, 3, GIVEN_PLAN, 1);

        assertEquals(GIVEN_PLAN, result.plan());
    }

    @Test
    @DisplayName("an unknown type drops the plan but keeps the distractors")
    void unknownTypeDropped() throws Exception {
        OpenAiClient client = clientAnswering(
            "{\"distractors\":[\"total -= i\"],\"plan\":{\"type\":\"WRONG_SHAPE\",\"rationale\":\"no\"}}"
        );

        GeneratedDistractors result = client.generateDistractors(SOLUTION, "python", "Sum", 1, 3, null, 1);

        assertEquals(1, result.lines().size());
        assertNull(result.plan());
    }

    @SuppressWarnings("unchecked")
    private OpenAiClient clientAnswering(String content) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        ObjectNode body = MAPPER.createObjectNode();
        body.putArray("choices").addObject().putObject("message").put("content", content);
        when(response.body()).thenReturn(body.toString());

        AiTransport transport = mock(AiTransport.class);
        when(transport.sendRequest(any())).thenReturn(response);

        return new OpenAiClient(
            "test-key", transport, promptBuilder, new AiProps(), null, mock(CodeSanitizerService.class)
        );
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }
}
