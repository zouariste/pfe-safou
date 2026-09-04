/*
 * Copyright (C) 2026 CodeOwl contributors
 *
 * This file is part of CodeOwl.
 *
 * CodeOwl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * CodeOwl is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CodeOwl. If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_passau.fim.se2.infrastructure.persistence.dao;

import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.domain.exceptions.*;
import de.uni_passau.fim.se2.domain.interfaces.*;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.WorksheetRepository;
import lombok.extern.slf4j.*;
import org.springframework.dao.*;
import de.uni_passau.fim.se2.domain.model.WorksheetVisibility;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.*;

import java.sql.Array;
import java.sql.SQLException;
import java.util.*;
import java.util.Optional;

/**
 * PostgreSQL-based implementation of the {@link WorksheetRepository}.
 * Provides CRUD operations for worksheet persistence using {@link JdbcTemplate}.
 * <p>
 * Each worksheet acts as a container for one or more {@link TieredTaskSequenceDTO} entities.
 * This DAO handles the worksheet metadata itself and the linkage to sequences via
 * the worksheet_sequence join table, but does not eagerly load tasks.
 */
@Slf4j
@Repository
public class PostgreWorksheetDao implements WorksheetRepository {

    private final JdbcTemplate jdbc;

    public PostgreWorksheetDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void createWorksheet(WorksheetDTO worksheet) {
        validateCreate(worksheet);

        // created_at is left to the column default: the database is the authority on when a row
        // came into existence, and a client clock has no business deciding marketplace ordering.
        String sql = """
                INSERT INTO worksheets (id, name, description, owner_id, hints_enabled,
                                        hint_credits_per_task, visibility, copied_from, copied_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        UUID id = UUID.randomUUID();

        jdbc.update(sql,
                    id,
                    worksheet.getName(),
                    worksheet.getDescription(),
                    worksheet.getOwnerId(),
                    worksheet.isHintsEnabled(),
                    worksheet.getHintCreditsPerTask(),
                    visibilityOf(worksheet).name(),
                    worksheet.getCopiedFrom(),
                    toTimestamp(worksheet.getCopiedAt())
        );

        worksheet.setId(id);
        log.info("Created worksheet: id={}, name='{}'", id, worksheet.getName());
    }

    @Override
    public void updateWorksheet(WorksheetDTO worksheet) {
        validateId(worksheet);

        String sql = """
                UPDATE worksheets
                SET name = ?, description = ?, hints_enabled = ?,
                    hint_credits_per_task = ?
                WHERE id = ?
                """;

        int rows = jdbc.update(sql,
                               worksheet.getName(),
                               worksheet.getDescription(),
                               worksheet.isHintsEnabled(),
                               worksheet.getHintCreditsPerTask(),
                               worksheet.getId()
        );

        if (rows == 0) {
            throw new DomainValidationException("No worksheet found for id=" + worksheet.getId());
        }

        log.info("Updated worksheet: id={}, name='{}'", worksheet.getId(), worksheet.getName());
    }

    @Override
    public void deleteWorksheet(WorksheetDTO worksheet) {
        validateId(worksheet);

        jdbc.update("DELETE FROM worksheets_sequence WHERE worksheet_id = ?", worksheet.getId());
        int rows = jdbc.update("DELETE FROM worksheets WHERE id = ?", worksheet.getId());

        if (rows == 0) {
            throw new DomainValidationException("No worksheet found for id=" + worksheet.getId());
        }

        log.info("Deleted worksheet: id={}, name='{}'", worksheet.getId(), worksheet.getName());
    }

    /**
     * One worksheet with the ids of the sequences on it.
     *
     * <p>Shared by both read paths below so there is one query and one mapper rather than two
     * that have to be kept in step — the columns here have already been widened twice.
     */
    private static final String SELECT_ONE = """
        SELECT
            w.id,
            w.name,
            w.description,
            w.owner_id,
            w.hints_enabled,
            w.hint_credits_per_task,
            w.visibility,
            w.created_at,
            w.copied_from,
            w.copied_at,
            COALESCE(array_agg(ws.sequence_id ORDER BY ws.sequence_id), '{}') AS sequence_ids
        FROM worksheets w
        LEFT JOIN worksheets_sequence ws ON ws.worksheet_id = w.id
        WHERE w.id = ?
        GROUP BY w.id, w.name, w.description, w.owner_id, w.hints_enabled,
                 w.hint_credits_per_task, w.visibility, w.created_at, w.copied_from, w.copied_at
        """;

    private static final RowMapper<WorksheetDTO> WORKSHEET_MAPPER = (rs, rowNum) ->
            WorksheetDTO.builder()
                .id(UUID.fromString(rs.getString("id")))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .ownerId(rs.getObject("owner_id", UUID.class))
                .hintsEnabled(rs.getBoolean("hints_enabled"))
                .hintCreditsPerTask((Integer) rs.getObject("hint_credits_per_task"))
                .visibility(WorksheetVisibility.parse(rs.getString("visibility")))
                .createdAt(toInstant(rs.getTimestamp("created_at")))
                .copiedFrom(rs.getObject("copied_from", UUID.class))
                .copiedAt(toInstant(rs.getTimestamp("copied_at")))
                    .sequenceDTOS(sequenceReferences(rs.getArray("sequence_ids")))
                    .build();

    @Override
    public Optional<WorksheetDTO> findById(UUID worksheetId) {
        if (worksheetId == null) {
            return Optional.empty();
        }
        List<WorksheetDTO> rows = jdbc.query(SELECT_ONE, WORKSHEET_MAPPER, worksheetId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Loads a worksheet including the identifiers of all associated tiered task sequences,
     * into the DTO it is handed.
     *
     * <p>Kept for the callers that rely on it throwing when the worksheet does not exist. New
     * code should prefer {@link #findById(UUID)}, which says so without an exception.
     *
     * @param worksheet the {@link WorksheetDTO} instance to populate with data
     *                  corresponding to its unique identifier.
     */
    @Override
    public void getWorksheetById(WorksheetDTO worksheet) {
        validateId(worksheet);

        WorksheetDTO result = findById(worksheet.getId()).orElseThrow(() ->
                new DomainValidationException("Worksheet not found for id=" + worksheet.getId()));

        worksheet.setName(result.getName());
        worksheet.setDescription(result.getDescription());
        worksheet.setOwnerId(result.getOwnerId());
        worksheet.setHintsEnabled(result.isHintsEnabled());
        worksheet.setHintCreditsPerTask(result.getHintCreditsPerTask());
        worksheet.setVisibility(result.getVisibility());
        worksheet.setCreatedAt(result.getCreatedAt());
        worksheet.setCopiedFrom(result.getCopiedFrom());
        worksheet.setCopiedAt(result.getCopiedAt());
        worksheet.setSequenceDTOS(result.getSequenceDTOS());

        log.debug("Loaded worksheet id={} with {} sequence references",
                  worksheet.getId(), result.getSequenceDTOS().size());
    }

    /**
     * Associates a single {@link TieredTaskSequenceDTO} with the specified {@link WorksheetDTO}.
     * <p>
     * This operation creates a new entry in the {@code worksheets_sequence} join table,
     * effectively linking the given worksheet and sequence.
     * If the association already exists, implementations should ensure idempotency
     * (i.e., not duplicate the relationship).
     *
     * @param worksheet the worksheet to which the sequence should be added; must not be {@code null}
     * @param sequence  the sequence to associate with the given worksheet; must not be {@code null}
     * @throws DomainValidationException if either the worksheet or sequence does not exist,
     *                                   or if validation of identifiers fails
     */
    @Override
    public void addSequenceToWorksheet(WorksheetDTO worksheet, TieredTaskSequenceDTO sequence) {
        validateId(worksheet);

        if (sequence == null || sequence.getId() == null) {
            log.warn("Sequence validation failed: null sequence or id");
            throw new DomainValidationException("Sequence or its ID cannot be null");
        }

        // PostgreSQL idiom for idempotent insert
        String sql = """
            INSERT INTO worksheets_sequence (worksheet_id, sequence_id)
            VALUES (?, ?)
            ON CONFLICT (worksheet_id, sequence_id) DO NOTHING
            """;

        try {
            int rows = jdbc.update(sql, worksheet.getId(), sequence.getId());

            if (rows > 0) {
                log.info("Linked sequence id={} to worksheet id={}", sequence.getId(), worksheet.getId());
            } else {
                log.debug("Sequence id={} already linked to worksheet id={} (no-op)",
                          sequence.getId(), worksheet.getId());
            }

        } catch (DataAccessException e) {
            log.error("Error linking sequence id={} to worksheet id={}", sequence.getId(), worksheet.getId(), e);
            throw new DomainValidationException("Failed to add sequence to worksheet: " + e.getMessage());
        }
    }

    /**
     * Associates multiple {@link TieredTaskSequenceDTO} objects with the specified {@link WorksheetDTO}.
     * <p>
     * This method performs a batch operation to add multiple sequence references to the worksheet
     * in the {@code worksheets_sequence} join table.
     * Implementations should aim for transactional integrity — all associations are added
     * successfully or none at all.
     *
     * @param worksheet the worksheet to which the sequences should be added; must not be {@code null}
     * @param sequences a list of sequences to associate with the given worksheet; must not be {@code null} or empty
     * @throws DomainValidationException if the worksheet does not exist, any sequence is invalid,
     *                                   or a data integrity violation occurs
     */
    @Override
    public void addSequencesToWorksheet(WorksheetDTO worksheet, List<TieredTaskSequenceDTO> sequences) {
        validateId(worksheet);

        if (sequences == null || sequences.isEmpty()) {
            log.warn("Sequence list is null or empty for worksheet id={}", worksheet.getId());
            throw new DomainValidationException("Sequence list cannot be null or empty");
        }

        // Validate all IDs before attempting batch insert
        for (TieredTaskSequenceDTO seq : sequences) {
            if (seq == null || seq.getId() == null) {
                throw new DomainValidationException("All sequences must have a non-null ID");
            }
        }

        String sql = """
            INSERT INTO worksheets_sequence (worksheet_id, sequence_id)
            VALUES (?, ?)
            ON CONFLICT (worksheet_id, sequence_id) DO NOTHING
            """;

        try {
            jdbc.batchUpdate(sql, sequences, sequences.size(),
                             (ps, seq) -> {
                                 ps.setObject(1, worksheet.getId());
                                 ps.setObject(2, seq.getId());
                             });

            log.info("Linked {} sequences to worksheet id={}", sequences.size(), worksheet.getId());

        } catch (DataAccessException e) {
            log.error("Error batch-linking sequences to worksheet id={}", worksheet.getId(), e);
            throw new DomainValidationException("Failed to add sequences to worksheet: " + e.getMessage());
        }
    }

    @Override
    public void removeSequenceFromWorksheet(WorksheetDTO worksheet, TieredTaskSequenceDTO sequence) {
        validateId(worksheet);

        if (sequence == null || sequence.getId() == null) {
            log.warn("Sequence validation failed: null sequence or id");
            throw new DomainValidationException("Sequence or its ID cannot be null");
        }

        String sql = """
        DELETE FROM worksheets_sequence
        WHERE worksheet_id = ? AND sequence_id = ?
        """;

        try {
            int rows = jdbc.update(sql, worksheet.getId(), sequence.getId());

            if (rows > 0) {
                log.info("Unlinked sequence id={} from worksheet id={}", sequence.getId(), worksheet.getId());
            } else {
                log.debug("No link found for sequence id={} and worksheet id={} (no-op)",
                          sequence.getId(), worksheet.getId());
            }

        } catch (DataAccessException e) {
            log.error("Error unlinking sequence id={} from worksheet id={}", sequence.getId(), worksheet.getId(), e);
            throw new DomainValidationException("Failed to remove sequence from worksheet: " + e.getMessage());
        }
    }

    /**
     * Lists a user's worksheets, each with the identifiers of its sequences.
     *
     * Like {@link #getWorksheetById} this loads sequence references only, not the
     * sequences themselves — enough for the overview to say how many there are
     * and for a caller to check whether a given sequence is already filed.
     */
    @Override
    public List<WorksheetDTO> findByOwnerId(UUID ownerId) {
        String sql = """
            SELECT w.id, w.name, w.description, w.owner_id,
                   w.hints_enabled, w.hint_credits_per_task,
                   w.visibility, w.created_at, w.copied_from, w.copied_at,
                   COALESCE(array_agg(ws.sequence_id ORDER BY ws.sequence_id), '{}') AS sequence_ids
            FROM worksheets w
            LEFT JOIN worksheets_sequence ws ON ws.worksheet_id = w.id
            WHERE w.owner_id = ?
            GROUP BY w.id, w.name, w.description, w.owner_id, w.hints_enabled,
                     w.hint_credits_per_task, w.visibility, w.created_at,
                     w.copied_from, w.copied_at
            ORDER BY w.name
            """;

        return jdbc.query(sql, (rs, rowNum) -> WorksheetDTO.builder()
                .id(UUID.fromString(rs.getString("id")))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .ownerId(rs.getObject("owner_id", UUID.class))
                .hintsEnabled(rs.getBoolean("hints_enabled"))
                .hintCreditsPerTask((Integer) rs.getObject("hint_credits_per_task"))
                .sequenceDTOS(sequenceReferences(rs.getArray("sequence_ids")))
                .visibility(WorksheetVisibility.parse(rs.getString("visibility")))
                .createdAt(toInstant(rs.getTimestamp("created_at")))
                .copiedFrom(rs.getObject("copied_from", UUID.class))
                .copiedAt(toInstant(rs.getTimestamp("copied_at")))
                .build(), ownerId);
    }

    @Override
    public void updateVisibility(UUID worksheetId, WorksheetVisibility visibility) {
        if (worksheetId == null) {
            throw new DomainValidationException("Worksheet ID cannot be null");
        }
        WorksheetVisibility target = visibility == null ? WorksheetVisibility.DEFAULT : visibility;

        // The enum's name() is bound as a parameter and the column carries a CHECK constraint,
        // so an unknown value cannot reach the table even if a caller invents one.
        int rows = jdbc.update("UPDATE worksheets SET visibility = ? WHERE id = ?",
                               target.name(), worksheetId);
        if (rows == 0) {
            throw new DomainValidationException("Worksheet not found for id=" + worksheetId);
        }
        log.info("Worksheet {} visibility set to {}", worksheetId, target);
    }

    /** {@code timestamptz} to {@link Instant}; null stays null. */
    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    /** {@link Instant} back to a bindable {@code timestamptz}; null stays null. */
    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    /** A worksheet built before the field existed carries none; it gets today's behaviour. */
    private static WorksheetVisibility visibilityOf(WorksheetDTO worksheet) {
        return worksheet.getVisibility() == null
                ? WorksheetVisibility.DEFAULT
                : worksheet.getVisibility();
    }

    /**
     * Turns an aggregated {@code sequence_id} array into id-only sequence DTOs.
     *
     * A worksheet with no sequences aggregates to a single NULL element rather
     * than an empty array, so nulls are dropped instead of becoming DTOs
     * without an id.
     */
    private static List<TieredTaskSequenceDTO> sequenceReferences(Array column) throws SQLException {
        List<TieredTaskSequenceDTO> sequences = new ArrayList<>();
        for (UUID seqId : (UUID[]) column.getArray()) {
            if (seqId != null) {
                sequences.add(TieredTaskSequenceDTO.builder().id(seqId).build());
            }
        }
        return sequences;
    }

    private void validateId(WorksheetDTO worksheet) {
        if (worksheet == null || worksheet.getId() == null) {
            log.warn("Worksheet validation failed: id=null");
            throw new DomainValidationException("Worksheet or ID cannot be null");
        }
    }

    private void validateCreate(WorksheetDTO worksheet) {
        if (worksheet == null) {
            throw new DomainValidationException("Worksheet cannot be null");
        }

        List<String> problems = new ArrayList<>();

        if (worksheet.getName() == null || worksheet.getName().isBlank()) {
            problems.add("name missing");
        }

        if (!problems.isEmpty()) {
            String msg = "Worksheet validation failed: " + String.join(", ", problems);
            log.warn("{} — worksheet={}", msg, worksheet);
            throw new DomainValidationException(msg);
        }
    }
}