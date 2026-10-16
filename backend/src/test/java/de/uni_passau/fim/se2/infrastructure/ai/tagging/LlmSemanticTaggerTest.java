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
package de.uni_passau.fim.se2.infrastructure.ai.tagging;

import de.uni_passau.fim.se2.domain.interfaces.AiModelClient;
import de.uni_passau.fim.se2.infrastructure.ai.IdentityLoader;
import de.uni_passau.fim.se2.infrastructure.ai.tagging.LlmSemanticTagger.SemanticTagResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Parsing of the semantic tagger's bilingual tag output.
 */
class LlmSemanticTaggerTest {

    private SemanticTagResult tagWithResponse(String response) throws Exception {
        AiModelClient client = mock(AiModelClient.class);
        when(client.generateSemanticTags(anyString(), anyString())).thenReturn(response);
        LlmSemanticTagger tagger = new LlmSemanticTagger(client, new IdentityLoader("classpath:PromptStuff/Identity/"));
        return tagger.tag("python", "statement", "print(1)");
    }

    @Test
    void readsGermanTagsAndEnglishTranslationsFromPairs() throws Exception {
        SemanticTagResult result = tagWithResponse("""
                {
                  "primary_tags": [
                    {"de": "fakultaet", "en": "factorial"},
                    {"de": "quicksort", "en": "quicksort"}
                  ],
                  "aliases": [{"de": "Fakultäts-Funktion", "en": "factorial function"}],
                  "short_description": "Berechnet die Fakultät."
                }
                """);

        assertEquals(List.of("fakultaet", "quicksort"), result.primaryTags());
        assertEquals(List.of("fakult-ts-funktion"), result.aliases());
        assertEquals(Map.of(
                "fakultaet", "factorial",
                "quicksort", "quicksort",
                "fakult-ts-funktion", "factorial-function"), result.translations());
        assertEquals("Berechnet die Fakultät.", result.shortDescription());
    }

    @Test
    void acceptsPlainStringTagsWithoutTranslation() throws Exception {
        SemanticTagResult result = tagWithResponse("""
                {"primary_tags": ["fibonacci", {"de": "zahlenfolge"}], "aliases": []}
                """);

        assertEquals(List.of("fibonacci", "zahlenfolge"), result.primaryTags());
        assertEquals(Map.of(), result.translations());
    }

    @Test
    void returnsNullWithoutPrimaryTags() throws Exception {
        assertNull(tagWithResponse("{\"primary_tags\": [], \"aliases\": []}"));
    }
}
