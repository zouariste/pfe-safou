package de.uni_passau.fim.se2.api.dto;

import java.util.List;

/**
 * Wrapper for {@code GET /api/library/sequences}.
 *
 * <p>{@code facets} are cheap to compute alongside the main query (same WHERE clause with a
 * GROUP BY) and let the Library sidebar show tag / language / ownership counts without a
 * second round-trip. {@code suggestions} is only populated when the FTS query returned zero
 * results — the server computes trigram-similar tag names as "Did you mean…" alternatives.
 */
public record LibrarySearchResponse(
        List<LibraryItemDTO> items,
        int page,
        int size,
        long total,
        Facets facets,
        List<String> suggestions
) {
    /** Facet counts for the filter sidebar. All lists are sorted by descending count. */
    public record Facets(
            List<FacetBucket> languages,
            List<FacetBucket> topTags,
            Ownership ownership
    ) {}

    /** A single facet entry: value → count. */
    public record FacetBucket(String name, long count) {}

    /** Split of sequences by "mine vs. everyone else" — helps the Library sidebar "My sequences" toggle. */
    public record Ownership(long mine, long others) {}
}
