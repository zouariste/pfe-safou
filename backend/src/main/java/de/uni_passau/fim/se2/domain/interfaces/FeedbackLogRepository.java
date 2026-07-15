package de.uni_passau.fim.se2.domain.interfaces;

import de.uni_passau.fim.se2.domain.entities.FeedbackLogDTO;
import java.util.List;
import java.util.UUID;

public interface FeedbackLogRepository {

    /**
     * Persists a feedback log entry.
     */
    void save(FeedbackLogDTO log);

    /**
     * Retrieves all feedback logs for a specific task.
     */
    List<FeedbackLogDTO> findByTaskId(UUID taskId);

    /**
     * Deletes all feedback logs for a given task (e.g., cleanup on cascade).
     */
    void deleteByTaskId(UUID taskId);

    /**
     * Retrieves all feedback logs for a specific class.
     */
    List<FeedbackLogDTO> findByClassId(UUID classId);
}