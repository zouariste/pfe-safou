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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.model.LocLimitWarning.TaskLoc;

@DisplayName("line limit per task")
class SequenceValidatorLocLimitTest {

    @Test
    @DisplayName("tasks at or under the limit are fine")
    void noneOver() {
        TieredTaskSequenceDTO seq = sequence(5, 20, 12);

        assertTrue(SequenceValidator.tasksOverLocLimit(seq, 20).isEmpty());
        assertDoesNotThrow(() -> SequenceValidator.validateLocLimit(seq, 20));
    }

    @Test
    @DisplayName("tasks over the limit are listed in tier order with their lines")
    void someOver() {
        TieredTaskSequenceDTO seq = sequence(31, 8, 21);
        // Out of tier order on purpose.
        seq.setTasks(new ArrayList<>(List.of(seq.getTasks().get(2), seq.getTasks().get(0), seq.getTasks().get(1))));

        assertEquals(
            List.of(new TaskLoc(1, 31), new TaskLoc(3, 21)),
            SequenceValidator.tasksOverLocLimit(seq, 20)
        );
        assertThrows(IllegalStateException.class, () -> SequenceValidator.validateLocLimit(seq, 20));
    }

    /** A sequence whose task for tier i+1 has {@code locs[i]} lines. */
    static TieredTaskSequenceDTO sequence(int... locs) {
        List<TaskDTO> tasks = new ArrayList<>();
        for (int i = 0; i < locs.length; i++) {
            TaskDTO task = new TaskDTO();
            task.setTierLevel(i + 1);
            task.setLinesOfCode(locs[i]);
            tasks.add(task);
        }
        return TieredTaskSequenceDTO.builder().tasks(tasks).build();
    }
}
