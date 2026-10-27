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

import de.uni_passau.fim.se2.domain.entities.ClassDTO;

public interface ClassRepository {

    ClassDTO save(ClassDTO classDTO);

    Optional<ClassDTO> findById(UUID id);

    /**
     * Load class with students and worksheets
     *
     * @param id the class to load
     * @return the class with its students and worksheets, or empty if it does not exist
     */
    Optional<ClassDTO> findByIdWithDetails(UUID id);

    List<ClassDTO> findByOwnerId(UUID ownerId);

    /**
     * Admin: find all classes
     *
     * @return every class
     */
    List<ClassDTO> findAll();

    void update(ClassDTO classDTO);

    void deleteById(UUID id);

    void addStudent(UUID classId, UUID studentId);

    void removeStudent(UUID classId, UUID studentId);

    void addWorksheet(UUID classId, UUID worksheetId);

    void removeWorksheet(UUID classId, UUID worksheetId);

    /**
     * Points one class's assignment at a different worksheet, keeping its schedule and published state.
     *
     * @param classId        the class
     * @param oldWorksheetId the worksheet currently assigned
     * @param newWorksheetId the worksheet to assign instead
     * @throws de.uni_passau.fim.se2.domain.exceptions.DomainValidationException if the old worksheet is not assigned to
     *                                                                           the class
     */
    void replaceWorksheet(UUID classId, UUID oldWorksheetId, UUID newWorksheetId);

    /**
     * Find classes a student belongs to
     *
     * @param studentId the student
     * @return the classes the student belongs to
     */
    List<ClassDTO> findByStudentId(UUID studentId);

    /**
     * Update tracking_enabled flag
     *
     * @param classId the class
     * @param enabled whether to track progress
     */
    void updateTrackingEnabled(UUID classId, boolean enabled);

    /**
     * Check if tracking is enabled for a class
     *
     * @param classId the class
     * @return true if progress tracking is enabled
     */
    boolean isTrackingEnabled(UUID classId);

    /**
     * Find the class ID for a student (first class found)
     *
     * @param studentId the student
     * @return the ID of a class the student belongs to, or null if none
     */
    UUID findClassIdForStudent(UUID studentId);
}
