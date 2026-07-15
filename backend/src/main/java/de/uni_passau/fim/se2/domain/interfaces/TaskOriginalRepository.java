package de.uni_passau.fim.se2.domain.interfaces;

import de.uni_passau.fim.se2.domain.entities.TaskOriginalDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for task originals (AP9 — Task-Bearbeitung & Versionierung).
 */
public interface TaskOriginalRepository {

    /**
     * Saves a task original record.
     */
    void save(TaskOriginalDTO original);

    /**
     * Finds the original version of a task, if one has been saved.
     */
    Optional<TaskOriginalDTO> findByTaskId(UUID taskId);

    /**
     * Checks whether an original already exists for the given task.
     */
    boolean existsByTaskId(UUID taskId);

    List<TaskOriginalDTO> findAll();
}
