package de.uni_passau.fim.se2.domain.entities;

import lombok.*;

import java.util.*;

@Data
public class TieredTaskSequenceTaskDTO {

    /**
     * The unique identifier for a task in the sequence.
     *
     * @return the taskId
     */
    UUID taskId;

    /**
     * The unique identifier that represents the sequence to which a task belongs.
     *
     * @return the sequenceId
     */
    UUID sequenceId;
}