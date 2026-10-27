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
import java.util.UUID;

import de.uni_passau.fim.se2.domain.entities.FeedbackLogDTO;

public interface FeedbackLogRepository {

    /**
     * Persists a feedback log entry.
     *
     * @param log the entry to save
     */
    void save(FeedbackLogDTO log);

    /**
     * Retrieves all feedback logs for a specific task.
     *
     * @param taskId the task
     * @return the task's feedback logs
     */
    List<FeedbackLogDTO> findByTaskId(UUID taskId);

    /**
     * Deletes all feedback logs for a given task (e.g., cleanup on cascade).
     *
     * @param taskId the task
     */
    void deleteByTaskId(UUID taskId);

    /**
     * Retrieves all feedback logs for a specific class.
     *
     * @param classId the class
     * @return the class's feedback logs
     */
    List<FeedbackLogDTO> findByClassId(UUID classId);
}
