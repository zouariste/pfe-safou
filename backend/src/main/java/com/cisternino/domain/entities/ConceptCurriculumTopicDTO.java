package com.cisternino.domain.entities;

import lombok.*;

import java.util.*;

/**
 * Represents a data transfer object that associates a programming concept
 * with a curriculum topic.
 *
 * This class is used to establish a relationship between a specific concept
 * and its corresponding curriculum topic within the system.
 */
@Data
public class ConceptCurriculumTopicDTO {

    /**
     * The unique identifier for a programming concept.
     *
     * This identifier is used to associate a specific programming concept
     * with its corresponding curriculum topic in the context of determining
     * the relationship between conceptual knowledge and its placement
     * within the learning structure.
     */
    UUID conceptId;

    /**
     * The unique identifier for the curriculum topic associated with a programming concept.
     *
     * This identifier is used to establish a link between a specific programming concept
     * and its corresponding curriculum topic within the system.
     */
    UUID topicId;
}