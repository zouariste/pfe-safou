package com.cisternino.infrastructure.persistence.dao;

import com.cisternino.domain.entities.*;
import com.cisternino.domain.interfaces.*;
import org.springframework.stereotype.*;

import java.util.*;

@Repository
public class PostgreSequenceTaskDAO implements TieredTaskSequenceTaskRepository {
    /**
     * Persists the provided TieredTaskSequenceTaskDTO instance.
     * Typically used to associate a task with a specific tiered task sequence.
     *
     * @param dto the TieredTaskSequenceTaskDTO object containing the mapping of a task
     *            to a tiered task sequence, including taskId and sequenceId
     */
    @Override
    public void save(TieredTaskSequenceTaskDTO dto) {

    }

    /**
     * Retrieves all tasks associated with the given tiered task sequence.
     *
     * @param sequence the tiered task sequence for which tasks are to be retrieved
     * @return a list of tasks that belong to the specified sequence
     */
    @Override
    public List<TaskDTO> findAllBySequenceId(TieredTaskSequenceDTO sequence) {
        return List.of();
    }

    /**
     * Deletes the specified mapping between a tiered task sequence and a task.
     *
     * @param dto the data transfer object containing the task ID and sequence ID
     *            of the mapping to be deleted
     */
    @Override
    public void delete(TieredTaskSequenceTaskDTO dto) {

    }
}