package com.cisternino.application.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of a {@link BulkRetagService} job.
 *
 * <p>AP19 Teil A: admins poll {@code GET /api/admin/sequences/retag/{jobId}} and see
 * {@code processed/total} progress. Unlike the live {@code BulkRetagService.JobState}
 * counters, this record is safe to serialize / log / embed in responses.
 *
 * <p>Status values:
 * <ul>
 *   <li>{@code QUEUED}   &mdash; job created, worker not yet started</li>
 *   <li>{@code RUNNING}  &mdash; worker iterating candidates</li>
 *   <li>{@code DONE}     &mdash; all candidates processed (some may still have failed individually)</li>
 *   <li>{@code FAILED}   &mdash; fatal error aborted the loop; {@code errorMessage} is set</li>
 * </ul>
 */
public record BulkRetagJobStatus(
        UUID jobId,
        String status,
        String filter,
        int total,
        int processed,
        int succeeded,
        int failed,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage
) {
    public static final String QUEUED  = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String DONE    = "DONE";
    public static final String FAILED  = "FAILED";
}
