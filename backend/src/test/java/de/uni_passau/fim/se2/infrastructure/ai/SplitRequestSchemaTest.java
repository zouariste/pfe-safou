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
package de.uni_passau.fim.se2.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.uni_passau.fim.se2.domain.entities.SplitPlan;

/**
 * The split calls are held to a schema, not just to "some JSON object", and the schemas match what is read back.
 */
@DisplayName("split requests and their schemas")
class SplitRequestSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PromptBuilder promptBuilder = new PromptBuilder(MAPPER, new IdentityLoader("classpath:unused/"));

    private static void assertStrictSchema(JsonNode request, String name, JsonNode schema) {
        JsonNode format = request.path("response_format");
        assertEquals("json_schema", format.path("type").asText());
        assertEquals(name, format.path("json_schema").path("name").asText());
        assertTrue(format.path("json_schema").path("strict").asBoolean());
        assertEquals(schema, format.path("json_schema").path("schema"));
    }

    @Test
    @DisplayName("the split plan request is held to the split plan schema")
    void splitPlan() {
        assertStrictSchema(
            promptBuilder.buildSplitPlanRequest("for i in range(3):\n    print(i)", "python", "Print", 2),
            "split_plan_response", PromptBuilder.SPLIT_PLAN_SCHEMA
        );
    }

    @Test
    @DisplayName("the split distractor request is held to the split distractor schema")
    void splitDistractor() {
        assertStrictSchema(
            promptBuilder.buildSplitDistractorRequest(List.of("for i in ", "range(3)"), "", ":", "python", "Print", 2),
            "split_distractor_response", PromptBuilder.SPLIT_DISTRACTOR_SCHEMA
        );
    }

    @Test
    @DisplayName("an answer in the split plan schema reads back as a split plan")
    void splitPlanAnswerReadsBack() throws Exception {
        SplitPlan plan = MAPPER.readValue(
            "{\"splits\":[{\"lineIndex\":0,\"fragments\":[\"for i in \",\"range(3)\"],\"prefix\":\"\",\"suffix\":\":\"}]}",
            SplitPlan.class
        );

        assertEquals(List.of("for i in ", "range(3)"), plan.getSplits().get(0).getFragments());
        assertEquals(":", plan.getSplits().get(0).getSuffix());
    }
}
