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

import de.uni_passau.fim.se2.application.service.BulkConceptReclassifyService.BulkConceptReclassifyRequest;
import de.uni_passau.fim.se2.domain.interfaces.AiModelClient;
import de.uni_passau.fim.se2.domain.interfaces.ConceptRepository;
import de.uni_passau.fim.se2.domain.interfaces.TieredTaskSequenceRepository;
import de.uni_passau.fim.se2.domain.interfaces.TieredTaskSequenceRepository.RetagCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Progress counting of the bulk concept re-classifier: every candidate is processed exactly once,
 * whether it succeeds, is skipped or fails.
 */
class BulkConceptReclassifyServiceTest {

    @Test
    void countsEveryCandidateOnceWhateverTheOutcome() throws Exception {
        TieredTaskSequenceRepository sequenceRepo = mock(TieredTaskSequenceRepository.class);
        AiModelClient aiModelClient = mock(AiModelClient.class);
        ConceptRepository conceptRepo = mock(ConceptRepository.class);

        RetagCandidate noCode = new RetagCandidate(UUID.randomUUID(), "python", "", "  ");
        RetagCandidate emptyResult = new RetagCandidate(UUID.randomUUID(), "python", "", "empty()");
        RetagCandidate classified = new RetagCandidate(UUID.randomUUID(), "python", "", "loop()");
        RetagCandidate failing = new RetagCandidate(UUID.randomUUID(), "python", "", "boom()");
        when(sequenceRepo.findConceptReclassifyCandidates(anyBoolean(), any(), anyInt()))
                .thenReturn(List.of(noCode, emptyResult, classified, failing));
        when(aiModelClient.generateConcepts(eq("empty()"), anyString())).thenReturn(List.of());
        when(aiModelClient.generateConcepts(eq("loop()"), anyString())).thenReturn(List.of("for loop"));
        when(aiModelClient.generateConcepts(eq("boom()"), anyString())).thenThrow(new RuntimeException("LLM down"));
        when(conceptRepo.saveAll(any())).thenReturn(List.of(UUID.randomUUID()));

        // Run the worker inline so the job has finished when submit returns.
        BulkConceptReclassifyService service =
                new BulkConceptReclassifyService(sequenceRepo, aiModelClient, conceptRepo, Runnable::run);

        UUID jobId = service.submit(new BulkConceptReclassifyRequest()).jobId();
        BulkConceptReclassifyJobStatus status = service.status(jobId);

        assertAll(
                () -> assertEquals(BulkConceptReclassifyJobStatus.DONE, status.status()),
                () -> assertEquals(4, status.total()),
                () -> assertEquals(4, status.processed()),
                () -> assertEquals(1, status.succeeded()),
                () -> assertEquals(1, status.failed()));
    }
}
