package com.cisternino.domain.entities;

import lombok.*;

import java.util.*;

/**
 * Represents a data transfer object (DTO) that links a version
 * to a tiered task sequence.
 *
 * This class is typically used to manage the relationship between
 * a specific version of a task sequence and the sequence itself.
 */
@Data
public class TieredTaskSequenceVersionDTO {

    /**
     * The unique identifier for a version in the context of a tiered task sequence.
     * This ID is used to associate a specific version of a task sequence with its related data.
     */
    UUID versionId;

    /**
     * Represents the unique identifier of a tiered task sequence
     * associated with a specific version in the context of mapping
     * versioned sequences to their corresponding tasks.
     */
    UUID sequenceId;
}