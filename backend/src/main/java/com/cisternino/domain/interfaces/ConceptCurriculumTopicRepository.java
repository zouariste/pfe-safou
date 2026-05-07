package com.cisternino.domain.interfaces;

import com.cisternino.domain.entities.*;

import java.util.List;

/**
 * Repository interface responsible for managing the association
 * between programming concepts and curriculum topics.
 * Provides methods to persist, retrieve, and delete mappings
 * between these entities.
 */
public interface ConceptCurriculumTopicRepository {

    /**
     * Persists the specified ConceptCurriculumTopicDTO instance in the repository.
     * This method is used to save or update the association between a programming concept
     * and a curriculum topic.
     *
     * @param dto the ConceptCurriculumTopicDTO object containing the identifiers
     *            of a programming concept and a curriculum topic for the association to be saved
     */
    void save(ConceptCurriculumTopicDTO dto);

    /**
     * Retrieves all curriculum topics associated with the given programming concept.
     *
     * @param concept the programming concept for which associated curriculum topics are to be retrieved
     * @return a list of CurriculumTopicDTO objects linked to the specified concept, or an empty list if no associations are found
     */
    List<CurriculumTopicDTO> findAllByConcept(ConceptDTO concept);

    /**
     * Finds all programming concepts associated with a given curriculum topic.
     *
     * @param topic the curriculum topic for which the associated concepts are to be retrieved
     * @return a list of ConceptDTO objects representing the concepts linked to the specified curriculum topic
     */
    List<ConceptDTO> findAllByCurriculumTopic(CurriculumTopicDTO topic);

    /**
     * Deletes the given association between a programming concept and a curriculum topic.
     * This method removes the mapping identified by the provided data transfer object (DTO).
     *
     * @param dto the ConceptCurriculumTopicDTO containing the identifiers of the programming concept
     *            and the curriculum topic for the association to be deleted
     */
    void delete(ConceptCurriculumTopicDTO dto);
}