package com.cisternino.domain.interfaces;

import com.cisternino.domain.entities.*;

import java.util.List;
import java.util.UUID;

/**
 * Interface for managing the relationship between tasks and tiered task sequences.
 * Provides methods to save, retrieve, and delete mappings between tasks and sequences.
 */
public interface TieredTaskSequenceTaskRepository {

    /**
     * Persists the provided TieredTaskSequenceTaskDTO instance.
     * Typically used to associate a task with a specific tiered task sequence.
     *
     * @param dto the TieredTaskSequenceTaskDTO object containing the mapping of a task
     *            to a tiered task sequence, including taskId and sequenceId
     */
    void save(TieredTaskSequenceTaskDTO dto);

    /**
     * Retrieves all tasks associated with the given tiered task sequence.
     *
     * @param sequence the tiered task sequence for which tasks are to be retrieved
     * @return a list of tasks that belong to the specified sequence
     */
    List<TaskDTO> findAllBySequenceId(TieredTaskSequenceDTO sequence);

    /**
     * Deletes the specified mapping between a tiered task sequence and a task.
     *
     * @param dto the data transfer object containing the task ID and sequence ID
     *            of the mapping to be deleted
     */
    void delete(TieredTaskSequenceTaskDTO dto);
}