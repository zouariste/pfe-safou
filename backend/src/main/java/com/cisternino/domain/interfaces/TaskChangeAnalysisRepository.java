package com.cisternino.domain.interfaces;

import com.cisternino.domain.entities.TaskChangeAnalysisDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for task change analyses (AP12 — Change-Analysis-Engine).
 */
public interface TaskChangeAnalysisRepository {

    /**
     * Saves (upserts) a change-analysis record.
     */
    void save(TaskChangeAnalysisDTO analysis);

    /**
     * Finds the change analysis for a given task, if one exists.
     */
    Optional<TaskChangeAnalysisDTO> findByTaskId(UUID taskId);

    /**
     * Returns all change-analysis records (used by the export endpoint).
     */
    List<TaskChangeAnalysisDTO> findAll();

    /**
     * Checks whether an analysis already exists for the given task.
     */
    boolean existsByTaskId(UUID taskId);

    /**
     * Deletes the change analysis for the given task.
     */
    void deleteByTaskId(UUID taskId);
}
