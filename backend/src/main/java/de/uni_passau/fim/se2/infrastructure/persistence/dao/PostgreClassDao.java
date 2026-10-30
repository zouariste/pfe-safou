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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import de.uni_passau.fim.se2.domain.entities.ClassDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.ClassRepository;
import de.uni_passau.fim.se2.domain.model.WorksheetAvailability;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PostgreClassDao implements ClassRepository {

    private static final String TRACKING_ENABLED = "tracking_enabled";
    private static final String CREATED_AT = "created_at";
    private static final String UPDATED_AT = "updated_at";
    private static final String NAME = "name";
    private static final String DESCRIPTION = "description";

    private final JdbcTemplate jdbc;

    private static final String WORKSHEET_SELECT = """
        SELECT cw.class_id, w.id as worksheet_id, w.name, w.description,
               cw.published, cw.release_at, cw.deadline_at, cw.grace_minutes
        FROM class_worksheets cw
        JOIN worksheets w ON w.id = cw.worksheet_id
        """;

    /** The class's own order; ties (which a reorder never leaves) fall back to when the worksheet was assigned. */
    private static final String WORKSHEET_ORDER = " ORDER BY cw.position, cw.assigned_at, cw.worksheet_id";

    /** One assignment row, with its availability already resolved. */
    private static ClassDTO.WorksheetInfo worksheetRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Instant releaseAt = toInstant(rs.getTimestamp("release_at"));
        Instant deadlineAt = toInstant(rs.getTimestamp("deadline_at"));
        Integer grace = (Integer) rs.getObject("grace_minutes");
        boolean published = rs.getBoolean("published");
        return ClassDTO.WorksheetInfo.builder()
            .worksheetId(rs.getObject("worksheet_id", UUID.class))
            .name(rs.getString(NAME))
            .description(rs.getString(DESCRIPTION))
            .published(published)
            .releaseAt(releaseAt)
            .deadlineAt(deadlineAt)
            .graceMinutes(grace)
            // Resolved here so every caller agrees, and so the
            // client cannot disagree with the server about now.
            .availability(WorksheetAvailability.of(published, releaseAt, deadlineAt, grace, Instant.now()))
            .build();
    }

    /**
     * Fills in the worksheet assignments for a list of classes in one query.
     *
     * The overview needs deadlines, and loading them class by class would mean a query per card.
     */
    private void attachWorksheets(List<ClassDTO> classes) {
        if (classes.isEmpty()) {
            return;
        }

        List<UUID> ids = classes.stream().map(ClassDTO::getId).toList();
        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        Map<UUID, List<ClassDTO.WorksheetInfo>> byClass = new HashMap<>();

        jdbc.query(
            WORKSHEET_SELECT + " WHERE cw.class_id IN (" + placeholders + ")" + WORKSHEET_ORDER,
            rs -> {
                byClass.computeIfAbsent(rs.getObject("class_id", UUID.class), k -> new ArrayList<>())
                    .add(worksheetRow(rs));
            },
            ids.toArray()
        );

        classes.forEach(c -> c.setWorksheets(byClass.getOrDefault(c.getId(), List.of())));
    }

    @Override
    public ClassDTO save(ClassDTO classDTO) {
        if (classDTO.getId() == null) {
            classDTO.setId(UUID.randomUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        classDTO.setCreatedAt(now);
        classDTO.setUpdatedAt(now);

        jdbc.update(
            """
                INSERT INTO classes (id, name, description, owner_id, tracking_enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
            classDTO.getId(),
            classDTO.getName(),
            classDTO.getDescription(),
            classDTO.getOwnerId(),
            classDTO.isTrackingEnabled(),
            Timestamp.valueOf(classDTO.getCreatedAt()),
            Timestamp.valueOf(classDTO.getUpdatedAt())
        );

        log.info("Created class: id={}, name='{}'", classDTO.getId(), classDTO.getName());
        return classDTO;
    }

    @Override
    public Optional<ClassDTO> findById(UUID id) {
        List<ClassDTO> results = jdbc.query(
            "SELECT * FROM classes WHERE id = ?",
            PostgreClassDao::mapClass,
            id
        );
        return results.stream().findFirst();
    }

    @Override
    public Optional<ClassDTO> findByIdWithDetails(UUID id) {
        Optional<ClassDTO> classOpt = findById(id);
        if (classOpt.isEmpty()) {
            return Optional.empty();
        }

        ClassDTO classDTO = classOpt.get();

        // Load students
        List<ClassDTO.StudentInfo> students = jdbc.query(
            """
                SELECT u.user_id, u.first_name, u.last_name, u.email, u.fim_uid,
                       at.token_value as invite_token
                FROM class_students cs
                JOIN users u ON u.user_id = cs.student_id
                LEFT JOIN auth_tokens at ON at.user_id = u.user_id
                    AND at.token_type = 'STUDENT_INVITE'
                    AND at.used = FALSE
                    AND at.expires_at > NOW()
                WHERE cs.class_id = ?
                ORDER BY u.last_name, u.first_name
                """,
            (rs, rowNum) -> ClassDTO.StudentInfo.builder()
                .userId(rs.getObject("user_id", UUID.class))
                .firstName(rs.getString("first_name"))
                .lastName(rs.getString("last_name"))
                .email(rs.getString("email"))
                .inviteToken(rs.getString("invite_token"))
                .fimUid(rs.getString("fim_uid"))
                .build(),
            id
        );
        classDTO.setStudents(students);

        // Load worksheets
        List<ClassDTO.WorksheetInfo> worksheets = jdbc.query(
            WORKSHEET_SELECT + " WHERE cw.class_id = ?" + WORKSHEET_ORDER,
            (rs, rowNum) -> worksheetRow(rs),
            id
        );
        classDTO.setWorksheets(worksheets);

        return Optional.of(classDTO);
    }

    @Override
    public List<ClassDTO> findByOwnerId(UUID ownerId) {
        return jdbc.query(
            """
                SELECT c.*, COUNT(DISTINCT cs.student_id) as student_count,
                       COUNT(DISTINCT cw.worksheet_id) as worksheet_count
                FROM classes c
                LEFT JOIN class_students cs ON cs.class_id = c.id
                LEFT JOIN class_worksheets cw ON cw.class_id = c.id
                WHERE c.owner_id = ?
                GROUP BY c.id
                ORDER BY c.created_at DESC
                """,
            PostgreClassDao::mapClass,
            ownerId
        );
    }

    @Override
    public List<ClassDTO> findAll() {
        return jdbc.query(
            """
                SELECT c.*,
                       NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), '') AS owner_name,
                       u.email AS owner_email
                FROM classes c
                LEFT JOIN users u ON u.user_id = c.owner_id
                ORDER BY c.created_at DESC
                """,
            (rs, rowNum) -> classBuilder(rs)
                .ownerName(rs.getString("owner_name"))
                .ownerEmail(rs.getString("owner_email"))
                .build()
        );
    }

    @Override
    public void update(ClassDTO classDTO) {
        classDTO.setUpdatedAt(LocalDateTime.now());
        int rows = jdbc.update(
            """
                UPDATE classes SET name = ?, description = ?, updated_at = ?
                WHERE id = ?
                """,
            classDTO.getName(),
            classDTO.getDescription(),
            Timestamp.valueOf(classDTO.getUpdatedAt()),
            classDTO.getId()
        );
        if (rows == 0) {
            throw new DomainValidationException("Klasse nicht gefunden: id=" + classDTO.getId());
        }
        log.info("Updated class: id={}, name='{}'", classDTO.getId(), classDTO.getName());
    }

    @Override
    public void deleteById(UUID id) {
        // Cascading deletes handle class_students and class_worksheets
        int rows = jdbc.update("DELETE FROM classes WHERE id = ?", id);
        if (rows == 0) {
            throw new DomainValidationException("Klasse nicht gefunden: id=" + id);
        }
        log.info("Deleted class: id={}", id);
    }

    @Override
    public void addStudent(UUID classId, UUID studentId) {
        try {
            jdbc.update("""
                INSERT INTO class_students (class_id, student_id)
                VALUES (?, ?)
                ON CONFLICT (class_id, student_id) DO NOTHING
                """, classId, studentId);
            log.info("Added student {} to class {}", studentId, classId);
        }
        catch (DataAccessException e) {
            throw new DomainValidationException("Fehler beim Hinzufügen des Schülers: " + e.getMessage());
        }
    }

    @Override
    public void removeStudent(UUID classId, UUID studentId) {
        jdbc.update(
            "DELETE FROM class_students WHERE class_id = ? AND student_id = ?",
            classId, studentId
        );
        log.info("Removed student {} from class {}", studentId, classId);
    }

    /**
     * Assigns a worksheet at the end of the class's list. Assigning one that is already there changes nothing, its
     * place included.
     */
    @Override
    public void addWorksheet(UUID classId, UUID worksheetId) {
        try {
            jdbc.update("""
                INSERT INTO class_worksheets (class_id, worksheet_id, position)
                SELECT ?, ?, COALESCE(MAX(position) + 1, 0)
                FROM class_worksheets WHERE class_id = ?
                ON CONFLICT (class_id, worksheet_id) DO NOTHING
                """, classId, worksheetId, classId);
            log.info("Assigned worksheet {} to class {}", worksheetId, classId);
        }
        catch (DataAccessException e) {
            throw new DomainValidationException("Fehler beim Zuweisen des Worksheets: " + e.getMessage());
        }
    }

    @Override
    public void removeWorksheet(UUID classId, UUID worksheetId) {
        jdbc.update(
            "DELETE FROM class_worksheets WHERE class_id = ? AND worksheet_id = ?",
            classId, worksheetId
        );
        log.info("Removed worksheet {} from class {}", worksheetId, classId);
    }

    /**
     * Rewrites the position of every worksheet assigned to a class to match {@code orderedWorksheetIds}.
     *
     * <p>
     * The caller must name exactly the worksheets already assigned — no more, no fewer — so a stale client (a tab that
     * missed an assignment or removal elsewhere) cannot drop a worksheet's place or invent one. Checked up front so a
     * bad request fails before any row is touched.
     *
     * @throws DomainValidationException if the id set does not exactly match the class's current worksheets
     */
    @Override
    public void reorderWorksheets(UUID classId, List<UUID> orderedWorksheetIds) {
        if (orderedWorksheetIds == null || orderedWorksheetIds.isEmpty()) {
            throw new DomainValidationException("Worksheet order list cannot be null or empty");
        }

        List<UUID> current = jdbc.queryForList(
            "SELECT worksheet_id FROM class_worksheets WHERE class_id = ?",
            UUID.class, classId
        );

        if (
            current.size() != orderedWorksheetIds.size()
                || !new HashSet<>(current).equals(new HashSet<>(orderedWorksheetIds))
        ) {
            throw new DomainValidationException(
                "Reordering must include exactly the worksheets already assigned to the class"
            );
        }

        List<Object[]> batchArgs = new ArrayList<>(orderedWorksheetIds.size());
        for (int i = 0; i < orderedWorksheetIds.size(); i++) {
            batchArgs.add(new Object[] { i, classId, orderedWorksheetIds.get(i) });
        }
        jdbc.batchUpdate(
            "UPDATE class_worksheets SET position = ? WHERE class_id = ? AND worksheet_id = ?",
            batchArgs
        );
        log.info("Reordered {} worksheets in class {}", orderedWorksheetIds.size(), classId);
    }

    /**
     * Rewrites the worksheet id in place rather than removing and re-adding the row, so the release date, deadline,
     * grace period, published flag, assigned_at and position all carry over.
     */
    @Override
    public void replaceWorksheet(UUID classId, UUID oldWorksheetId, UUID newWorksheetId) {
        int rows = jdbc.update("""
            UPDATE class_worksheets SET worksheet_id = ?
            WHERE class_id = ? AND worksheet_id = ?
            """, newWorksheetId, classId, oldWorksheetId);
        if (rows == 0) {
            throw new DomainValidationException("That worksheet is not assigned to this class.");
        }
        log.info("Replaced worksheet {} with {} in class {}", oldWorksheetId, newWorksheetId, classId);
    }

    @Override
    public List<ClassDTO> findByStudentId(UUID studentId) {
        List<ClassDTO> classes = jdbc.query(
            """
                SELECT c.* FROM classes c
                JOIN class_students cs ON cs.class_id = c.id
                WHERE cs.student_id = ?
                ORDER BY c.name
                """,
            PostgreClassDao::mapClass,
            studentId
        );
        // The overview shows deadlines, so the assignments have to come with it.
        attachWorksheets(classes);
        return classes;
    }

    // ── Tracking (AP5) ──────────────────────────────────────

    @Override
    public void updateTrackingEnabled(UUID classId, boolean enabled) {
        int rows = jdbc.update(
            """
                UPDATE classes SET tracking_enabled = ?, updated_at = ?
                WHERE id = ?
                """,
            enabled,
            Timestamp.valueOf(LocalDateTime.now()),
            classId
        );
        if (rows == 0) {
            throw new DomainValidationException("Klasse nicht gefunden: id=" + classId);
        }
        log.info("Updated tracking_enabled={} for class id={}", enabled, classId);
    }

    @Override
    public boolean isTrackingEnabled(UUID classId) {
        List<Boolean> results = jdbc.query(
            "SELECT tracking_enabled FROM classes WHERE id = ?",
            (rs, rowNum) -> rs.getBoolean(TRACKING_ENABLED),
            classId
        );
        return results.stream().findFirst().orElse(false);
    }

    /**
     * Returns the class_id for a student (first class found). Used to determine tracking context when logging feedback.
     *
     * @param studentId the student
     * @return the ID of a class the student belongs to, or null if none
     */
    public UUID findClassIdForStudent(UUID studentId) {
        List<UUID> results = jdbc.query(
            "SELECT class_id FROM class_students WHERE student_id = ? LIMIT 1",
            (rs, rowNum) -> rs.getObject("class_id", UUID.class),
            studentId
        );
        return results.stream().findFirst().orElse(null);
    }

    /** Null-safe timestamp conversion; a null column means "no constraint". */
    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    /**
     * Publishes or unpublishes one assignment.
     *
     * Two things can release an assignment — the publish flag and a release date that has arrived — so taking it back
     * out of sight has to neutralise both. Clearing only the flag would leave a passed release date still releasing it,
     * and the button would appear to do nothing.
     *
     * @param classId     the class
     * @param worksheetId the worksheet assigned to the class
     * @param published   whether the class should see the worksheet
     */
    public void setWorksheetPublished(UUID classId, UUID worksheetId, boolean published) {
        int rows;
        if (published) {
            // Stamp the moment of publication into the schedule, the way ending
            // stamps the deadline, so the editor shows when it went live rather
            // than an empty field.
            //
            // Unless that would put the release after an already-passed deadline,
            // which the deadline_after_release constraint forbids. In that case
            // the flag alone publishes it and the schedule is left as the teacher
            // set it — a worksheet published after its deadline reads as closed,
            // which is the truth.
            Timestamp now = Timestamp.from(Instant.now());
            rows = jdbc.update("""
                UPDATE class_worksheets
                SET published = true,
                    release_at = CASE WHEN deadline_at IS NULL OR deadline_at > ?
                                      THEN ? ELSE release_at END
                WHERE class_id = ? AND worksheet_id = ?
                """, now, now, classId, worksheetId);
        }
        else {
            rows = jdbc.update("""
                UPDATE class_worksheets
                SET published = false, release_at = NULL
                WHERE class_id = ? AND worksheet_id = ?
                """, classId, worksheetId);
        }
        if (rows == 0) {
            throw new DomainValidationException("That worksheet is not assigned to this class.");
        }
    }

    /**
     * Ends an assignment now: the deadline becomes this moment with no grace, so it reads as closed immediately and the
     * reason is visible in the schedule rather than hidden in a separate flag.
     *
     * @param classId     the class
     * @param worksheetId the worksheet assigned to the class
     * @param now         the moment to use as the deadline
     */
    public void closeWorksheetNow(UUID classId, UUID worksheetId, Instant now) {
        int rows = jdbc.update("""
            UPDATE class_worksheets
            SET deadline_at = ?, grace_minutes = 0,
                published = true
            WHERE class_id = ? AND worksheet_id = ?
            """, Timestamp.from(now), classId, worksheetId);
        if (rows == 0) {
            throw new DomainValidationException("That worksheet is not assigned to this class.");
        }
    }

    /**
     * Sets the schedule for one worksheet in one class. Passing null for a field clears that constraint.
     *
     * @param classId      the class
     * @param worksheetId  the worksheet assigned to the class
     * @param releaseAt    when the worksheet becomes available, or null for at once
     * @param deadlineAt   when it closes, or null for never
     * @param graceMinutes how long after the deadline work is still accepted, or null for none
     */
    public void updateWorksheetSchedule(
        UUID classId, UUID worksheetId,
        Instant releaseAt, Instant deadlineAt, Integer graceMinutes
    ) {
        int rows = jdbc.update(
            """
                UPDATE class_worksheets
                SET release_at = ?, deadline_at = ?, grace_minutes = ?
                WHERE class_id = ? AND worksheet_id = ?
                """,
            releaseAt != null ? Timestamp.from(releaseAt) : null,
            deadlineAt != null ? Timestamp.from(deadlineAt) : null,
            graceMinutes,
            classId, worksheetId
        );

        if (rows == 0) {
            throw new DomainValidationException("That worksheet is not assigned to this class.");
        }
    }

    private static ClassDTO mapClass(ResultSet rs, int rowNum) throws SQLException {
        return classBuilder(rs).build();
    }

    /** The columns every class query selects; callers add what their query joins in. */
    private static ClassDTO.ClassDTOBuilder classBuilder(ResultSet rs) throws SQLException {
        return ClassDTO.builder()
            .id(rs.getObject("id", UUID.class))
            .name(rs.getString(NAME))
            .description(rs.getString(DESCRIPTION))
            .ownerId(rs.getObject("owner_id", UUID.class))
            .trackingEnabled(rs.getBoolean(TRACKING_ENABLED))
            .createdAt(toLocalDateTime(rs.getTimestamp(CREATED_AT)))
            .updatedAt(toLocalDateTime(rs.getTimestamp(UPDATED_AT)));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}
