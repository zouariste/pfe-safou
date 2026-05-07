package com.cisternino.application.events;

import java.util.UUID;

/**
 * Fired after a TieredTaskSequence has been fully generated and persisted.
 *
 * <p>Listeners should treat this as "the sequence exists in the DB and can be enriched
 * with background-computed metadata (tags, summaries, …)".
 *
 * <p>The {@code combinedCode} and {@code combinedStatement} fields are concatenations
 * across all tiers — they're what the LLM tagger sees. A single string is passed instead
 * of the full sequence DTO to keep the event payload minimal (events are copied across
 * async boundaries).
 */
public record SequenceGeneratedEvent(
        UUID sequenceId,
        String programmingLanguage,
        String combinedStatement,
        String combinedCode
) {
    public SequenceGeneratedEvent {
        if (sequenceId == null) throw new IllegalArgumentException("sequenceId must not be null");
    }
}
