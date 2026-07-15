package de.uni_passau.fim.se2.domain.interfaces;

import de.uni_passau.fim.se2.domain.entities.LearningEventDTO;

import java.util.List;
import java.util.UUID;

/**
 * Repository for learning analytics events (AP10).
 */
public interface LearningEventRepository {

    void save(LearningEventDTO event);

    List<LearningEventDTO> findByTaskId(UUID taskId);

    List<LearningEventDTO> findByUserId(UUID userId);

    List<LearningEventDTO> findBySequenceId(UUID sequenceId);

    List<LearningEventDTO> findAll();
}
