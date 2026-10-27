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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.uni_passau.fim.se2.domain.entities.SequenceTagDTO;
import de.uni_passau.fim.se2.domain.interfaces.SequenceTagRepository;
import de.uni_passau.fim.se2.domain.model.ConceptCatalog;

/**
 * LLM topics never name a concept: topics and concepts are kept disjoint even when the tagger ignores its prompt.
 */
class SequenceTagServiceTest {

    private static final UUID SEQUENCE = UUID.randomUUID();

    private SequenceTagRepository tagRepo;
    private SequenceTagService service;

    @BeforeEach
    void setUp() {
        // Which names count as concepts is ConceptCatalog's business (see ConceptCatalogTest).
        ConceptCatalog catalog = mock(ConceptCatalog.class);
        when(catalog.getConceptNamesAsTopicSlugs())
            .thenReturn(Set.of("linked-list", "stack", "queue", "rekursion"));

        tagRepo = mock(SequenceTagRepository.class);
        service = new SequenceTagService(tagRepo, catalog);
    }

    @SuppressWarnings("unchecked")
    private List<String> writtenTagNames() {
        ArgumentCaptor<List<SequenceTagDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(tagRepo).replaceForSource(eq(SEQUENCE), eq("llm"), captor.capture());
        return captor.getValue().stream().map(SequenceTagDTO::getTagName).toList();
    }

    @Test
    void dropsTagsNamingAConcept() {
        service.writeLlmTags(
            SEQUENCE,
            List.of("linked-list", "klammerpruefung", "rekursion"),
            List.of("stack"),
            Map.of()
        );

        assertEquals(List.of("klammerpruefung"), writtenTagNames());
    }

    @Test
    void dropsATagWhoseEnglishTranslationNamesAConcept() {
        service.writeLlmTags(
            SEQUENCE,
            List.of("warteschlange-x", "fibonacci"),
            List.of(),
            Map.of("warteschlange-x", "queue", "fibonacci", "fibonacci")
        );

        assertEquals(List.of("fibonacci"), writtenTagNames());
        verify(tagRepo).upsertTranslations(Map.of("fibonacci", "fibonacci"));
    }

    @Test
    void writesNothingWhenEveryTagNamesAConcept() {
        service.writeLlmTags(SEQUENCE, List.of("stack", "queue"), List.of(), Map.of());

        verify(tagRepo, never()).replaceForSource(any(), anyString(), any());
        verify(tagRepo, never()).upsertTranslations(any());
    }
}
