package com.cisternino.domain.interfaces;

import com.cisternino.domain.entities.SequenceTagDTO;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repository for sequence tags (AP19 Teil A).
 * Tags are keyed by (sequence_id, tag_name) and carry a source (auto / llm / manual).
 */
public interface SequenceTagRepository {

    /** All tags for a single sequence, ordered by source (manual first) then tag_name. */
    List<SequenceTagDTO> findBySequenceId(UUID sequenceId);

    /** All tags for a batch of sequences, grouped by sequenceId. Empty lists omitted. */
    Map<UUID, List<SequenceTagDTO>> findBySequenceIds(List<UUID> sequenceIds);

    /**
     * Upsert a batch of tags for a single sequence.
     * {@code ON CONFLICT DO NOTHING} — existing tags (esp. manual) are not overwritten.
     */
    void upsertAll(List<SequenceTagDTO> tags);

    /**
     * Replace all tags of a given source for a sequence (e.g. re-tagging structural tags
     * after a code change). Never touches tags with a different source.
     */
    void replaceForSource(UUID sequenceId, String source, List<SequenceTagDTO> newTags);

    /** Delete a single tag. */
    void delete(UUID sequenceId, String tagName);

    /** Distinct tag names across the system, with usage counts (for autocomplete / tag cloud). */
    List<TagCount> findAllTagsWithCounts();

    /** Distinct tag names that match the given prefix, limited for autocomplete. */
    List<String> findTagsByPrefix(String prefix, int limit);

    /**
     * AP19 A16: tags that first appeared after {@code cutoff}. Used by the admin "new topics"
     * weekly report so admins can spot emerging concepts and decide whether to merge / canonicalise.
     *
     * @param cutoff earliest first-seen timestamp to include (inclusive)
     * @param limit  max rows (1..200)
     * @return       list ordered by usage count DESC, then first_seen DESC
     */
    List<NewTopic> findNewTopicsSince(Instant cutoff, int limit);

    /**
     * AP19 A16: rename a tag across all sequences (e.g. "rekursion" → "recursion").
     * If {@code toTagName} already exists on a sequence that also has {@code fromTagName},
     * the row is dropped rather than duplicated, so the unique key is preserved. Source of the
     * merged row is kept from the earlier insertion.
     *
     * @return number of sequence_tags rows affected (renamed + deleted)
     */
    int mergeTags(String fromTagName, String toTagName);

    record TagCount(String tagName, long count) {}

    /**
     * A row in the admin "new topics" report.
     *
     * @param tagName    the tag
     * @param firstSeen  when the tag was first attached to any sequence
     * @param usageCount how many sequences currently carry it
     */
    record NewTopic(String tagName, Instant firstSeen, long usageCount) {}
}
