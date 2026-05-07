package com.cisternino.domain.interfaces;

import com.cisternino.domain.entities.ClassDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassRepository {

    ClassDTO save(ClassDTO classDTO);

    Optional<ClassDTO> findById(UUID id);

    /** Load class with students and worksheets */
    Optional<ClassDTO> findByIdWithDetails(UUID id);

    List<ClassDTO> findByOwnerId(UUID ownerId);

    /** Admin: find all classes */
    List<ClassDTO> findAll();

    void update(ClassDTO classDTO);

    void deleteById(UUID id);

    void addStudent(UUID classId, UUID studentId);

    void removeStudent(UUID classId, UUID studentId);

    void addWorksheet(UUID classId, UUID worksheetId);

    void removeWorksheet(UUID classId, UUID worksheetId);

    /** Find classes a student belongs to */
    List<ClassDTO> findByStudentId(UUID studentId);

    /** Update tracking_enabled flag */
    void updateTrackingEnabled(UUID classId, boolean enabled);

    /** Check if tracking is enabled for a class */
    boolean isTrackingEnabled(UUID classId);

    /** Find the class ID for a student (first class found) */
    UUID findClassIdForStudent(UUID studentId);
}
