package com.cisternino.domain.entities;

import lombok.*;

import java.util.*;

/**
 * Represents a data transfer object that associates a programming concept
 * with a specific tiered task sequence.
 * <p>
 * Each instance holds a unique identifier for a concept and a unique identifier
 * for a tiered task sequence. This DTO is commonly used to model the relationship
 * between task sequences and the concepts they cover.
 */
@Data
public class TieredTaskSequenceConceptDTO {

    /**
     * The unique identifier for a programming concept.
     * This identifier is used to associate a specific programming concept
     * with a tiered task sequence in the context of modeling relationships
     * between tasks and the concepts they cover.
     */
    UUID conceptId;

    /**
     * Represents the unique identifier for a specific tiered task sequence.
     * This identifier is used to associate a particular sequence of tasks with related programming concepts.
     */
    UUID sequenceId;
}