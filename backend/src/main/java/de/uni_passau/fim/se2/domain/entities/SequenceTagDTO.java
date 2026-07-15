package de.uni_passau.fim.se2.domain.entities;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single tag on a TieredTaskSequence (AP19 Teil A).
 *
 * <p>Tags have three sources, stored in the {@code source} column:
 * <ul>
 *   <li>{@code "auto"} &mdash; structural, derived from AP25 Concept Detection (for-loop, recursion, …)</li>
 *   <li>{@code "llm"} &mdash; semantic, derived from an LLM analysis (fibonacci, binary-search, …)</li>
 *   <li>{@code "manual"} &mdash; teacher-set, must never be overwritten by auto-retag</li>
 * </ul>
 */
@Data
@NoArgsConstructor
public class SequenceTagDTO {
    private UUID sequenceId;
    private String tagName;
    /** One of "auto", "llm", "manual". */
    private String source;
    /** Only populated for LLM-generated tags (0.0-1.0). */
    private Float confidence;
    private LocalDateTime createdAt;

    public SequenceTagDTO(UUID sequenceId, String tagName, String source) {
        this.sequenceId = sequenceId;
        this.tagName = tagName;
        this.source = source;
    }

    public SequenceTagDTO(UUID sequenceId, String tagName, String source, Float confidence) {
        this.sequenceId = sequenceId;
        this.tagName = tagName;
        this.source = source;
        this.confidence = confidence;
    }
}
