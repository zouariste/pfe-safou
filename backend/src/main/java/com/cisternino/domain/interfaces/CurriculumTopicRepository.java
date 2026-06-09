package com.cisternino.domain.interfaces;

import com.cisternino.domain.entities.CurriculumTopicDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing curriculum topics.
 * Provides methods for persisting, retrieving, and deleting instances
 * of CurriculumTopicDTO, which represent individual curriculum topics.
 */
public interface CurriculumTopicRepository {

    /**
     * Persists the specified curriculum topic data transfer object (CurriculumTopicDTO)
     * into the repository. This method ensures that the details of the provided curriculum topic,
     * such as its unique identifier, name, and description, are saved for future retrieval or updates.
     *
     * @param topic the CurriculumTopicDTO object representing the curriculum topic
     *              to be saved. It includes details like the topic's unique identifier, name,
     *              and description.
     */
    void save(CurriculumTopicDTO topic);

    /**
     * Retrieves all curriculum topics available in the repository.
     * This method provides a list of CurriculumTopicDTO objects,
     * which represent individual curriculum topics, including their
     * unique identifiers, names, and descriptions.
     *
     * @return a list of CurriculumTopicDTO objects representing all
     *         the curriculum topics in the repository, or an empty
     *         list if no topics are found
     */
    List<CurriculumTopicDTO> findAll();

    /**
     * Deletes the specified curriculum topic from the repository.
     * This method removes the given CurriculumTopicDTO instance along with
     * its associated data.
     *
     * @param topic the CurriculumTopicDTO object representing the curriculum topic to be deleted,
     *              including its unique identifier and other related details
     */
    void delete(CurriculumTopicDTO topic);

    Optional<CurriculumTopicDTO> findById(UUID id);

    List<CurriculumTopicDTO> findTopLevel();

    List<CurriculumTopicDTO> findByParentId(UUID parentId);
}