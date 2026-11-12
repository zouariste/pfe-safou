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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.uni_passau.fim.se2.domain.model.DistractorStrategy;

/**
 * The sequence identities take their distractor taxonomy and tier mapping from {@link DistractorStrategy}. A misspelt
 * placeholder would otherwise reach the model as literal {@code {{…}}} text without anything failing.
 */
class SequenceIdentityDistractorTest {

    private final PromptBuilder promptBuilder = new PromptBuilder(
        new ObjectMapper(), new IdentityLoader("classpath:PromptStuff/Identity/")
    );

    @ParameterizedTest(name = "{0} tiers")
    @CsvSource({ "3", "5" })
    void sequenceRequestCarriesTheSharedTaxonomyAndMapping(int tierCount) {
        ObjectNode request = promptBuilder.buildSequenceRequest(
            "Sum a list", List.of("loop"), null, "python", "en", true, null, tierCount
        );

        JsonNode messages = request.path("messages");
        String system = messages.path(0).path("content").asText();
        String user = messages.path(messages.size() - 1).path("content").asText();

        assertFalse(system.contains("{{"), "unfilled placeholder in the system prompt");
        assertTrue(system.contains(DistractorStrategy.taxonomyText()));
        assertTrue(system.contains(DistractorStrategy.tierMappingText(tierCount)));
        assertTrue(user.contains(DistractorStrategy.tierMappingText(tierCount)));
    }

    @ParameterizedTest(name = "{0} tiers")
    @CsvSource({ "3", "5" })
    void sequenceSchemaHoldsThePlanTypeToTheTaxonomy(int tierCount) {
        ObjectNode request = promptBuilder.buildSequenceRequest(
            "Sum a list", List.of("loop"), null, "python", "en", true, null, tierCount
        );

        JsonNode type = request.at(
            "/response_format/json_schema/schema/properties/tasks/items/properties/distractorPlan/properties/type"
        );
        assertEquals(
            Arrays.stream(DistractorStrategy.Type.values()).map(Enum::name).toList(),
            type.path("enum").valueStream().map(JsonNode::asText).toList()
        );
    }
}
