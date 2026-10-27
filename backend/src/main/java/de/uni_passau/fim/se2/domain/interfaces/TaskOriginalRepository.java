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
package de.uni_passau.fim.se2.domain.interfaces;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import de.uni_passau.fim.se2.domain.entities.TaskOriginalDTO;

/**
 * Repository for task originals (AP9 — Task-Bearbeitung & Versionierung).
 */
public interface TaskOriginalRepository {

    /**
     * Saves a task original record.
     *
     * @param original the original to save
     */
    void save(TaskOriginalDTO original);

    /**
     * Finds the original version of a task, if one has been saved.
     *
     * @param taskId the task
     * @return the task's original, or empty if none was saved
     */
    Optional<TaskOriginalDTO> findByTaskId(UUID taskId);

    /**
     * Checks whether an original already exists for the given task.
     *
     * @param taskId the task
     * @return true if the task has an original
     */
    boolean existsByTaskId(UUID taskId);

    List<TaskOriginalDTO> findAll();
}
