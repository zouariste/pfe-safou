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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.model.LocLimitWarning;
import de.uni_passau.fim.se2.infrastructure.ai.openai.OpenAiClient;

/**
 * A sequence with tasks over the line limit is saved and returned with a warning after one attempt, rather than
 * regenerated: the teacher decides whether to keep it.
 */
@DisplayName("generation with tasks over the line limit")
class TaskGeneratorServiceLocLimitTest {

    private final TaskPersistenceService persistence = mock(TaskPersistenceService.class);
    private TaskGeneratorService service;

    @BeforeEach
    void setUp() {
        service = spy(new TaskGeneratorService(mock(OpenAiClient.class), persistence, mock(TaskEnricher.class)));
        setField("maxAttempts", 3);
        setField("maxLocPerTask", 20);
    }

    /** The limits are {@code @Value} fields, normally injected by Spring. */
    private void setField(String name, int value) {
        try {
            var field = TaskGeneratorService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(service, value);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("an over-long sequence is saved after one attempt, with the tasks over the limit")
    void overLimitIsReturnedNotRetried() throws Exception {
        returns(SequenceValidatorLocLimitTest.sequence(10, 27, 15));

        TieredTaskSequenceDTO seq = generate();

        verifyGeneratedOnce();
        verify(persistence).persistSequence(seq);
        assertEquals(new LocLimitWarning(20, List.of(new LocLimitWarning.TaskLoc(2, 27))), seq.getLocWarning());
    }

    @Test
    @DisplayName("a sequence within the limit carries no warning")
    void withinLimitHasNoWarning() throws Exception {
        returns(SequenceValidatorLocLimitTest.sequence(10, 20, 15));

        TieredTaskSequenceDTO seq = generate();

        verifyGeneratedOnce();
        assertNotNull(seq);
        assertNull(seq.getLocWarning());
    }

    private void returns(TieredTaskSequenceDTO seq) throws Exception {
        doReturn(seq).when(service).tryGenerate(
            any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyInt(), anyBoolean()
        );
    }

    private TieredTaskSequenceDTO generate() throws Exception {
        return service.generate(
            "Sum a list", List.of(), "python", null, UUID.randomUUID(), "de",
            false, false, null, 3, false
        );
    }

    private void verifyGeneratedOnce() throws Exception {
        verify(service, times(1)).tryGenerate(
            any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyInt(), anyBoolean()
        );
    }
}
