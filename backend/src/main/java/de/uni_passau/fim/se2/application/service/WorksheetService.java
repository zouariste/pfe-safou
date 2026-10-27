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
package de.uni_passau.fim.se2.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.entities.UserDTO;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.WorksheetRepository;
import de.uni_passau.fim.se2.domain.model.WorksheetAccessPolicy;
import de.uni_passau.fim.se2.domain.model.WorksheetVisibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorksheetService {

    private static final String WORKSHEET_ID_REQUIRED = "Worksheet ID must not be null.";

    private final WorksheetRepository worksheetRepo;
    private final WorksheetCloneService cloneService;

    @Transactional
    public void createWorksheet(WorksheetDTO worksheet) {
        validateWorksheetForCreate(worksheet);
        worksheetRepo.createWorksheet(worksheet);
        log.debug("Created worksheet: {}", worksheet.getId());
    }

    @Transactional
    public void updateWorksheet(WorksheetDTO worksheet) {
        validateWorksheetId(worksheet);
        worksheetRepo.updateWorksheet(worksheet);
        log.debug("Updated worksheet id={}", worksheet.getId());
    }

    /**
     * Deletes a worksheet.
     *
     * @param id the worksheet to delete
     */
    @Transactional
    public void deleteWorksheet(UUID id) {
        if (id == null) {
            throw new DomainValidationException(WORKSHEET_ID_REQUIRED);
        }
        WorksheetDTO worksheet = WorksheetDTO.builder().id(id).build();
        worksheetRepo.deleteWorksheet(worksheet);
        log.debug("Deleted worksheet id={}", id);
    }

    /**
     * Loads a worksheet with its sequence references. Fails if it does not exist; use {@link #findWorksheetById(UUID)}
     * where that is a legitimate answer.
     *
     * @param id the worksheet to load
     * @return the worksheet
     */
    @Transactional(readOnly = true)
    public WorksheetDTO loadWorksheetById(UUID id) {
        if (id == null) {
            throw new DomainValidationException(WORKSHEET_ID_REQUIRED);
        }
        WorksheetDTO worksheet = WorksheetDTO.builder().id(id).build();
        worksheetRepo.getWorksheetById(worksheet);
        return worksheet;
    }

    /**
     * A worksheet, if it exists.
     *
     * <p>
     * Preferred over {@link #loadWorksheetById(UUID)} wherever the answer might legitimately be "there is none". That
     * method throws, which turns a missing worksheet into a 400 saying so — and an endpoint that answers 404 for a
     * worksheet the caller may not see then tells that caller which ids are real. An empty result lets both cases
     * answer alike.
     *
     * @param id the worksheet to look up
     * @return the worksheet, or empty if it does not exist
     */
    @Transactional(readOnly = true)
    public Optional<WorksheetDTO> findWorksheetById(UUID id) {
        return worksheetRepo.findById(id);
    }

    @Transactional(readOnly = true)
    public List<WorksheetDTO> findByOwnerId(UUID ownerId) {
        return worksheetRepo.findByOwnerId(ownerId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // AP19 Teil A (A12): "Library Inbox" default worksheet
    // ─────────────────────────────────────────────────────────────────────

    /**
     * A sentinel embedded in the description field of the auto-created "Library Inbox" worksheet. Lookup is done by
     * sentinel (not name) so a teacher can rename the worksheet freely without breaking the one-click-copy behavior.
     */
    public static final String LIBRARY_INBOX_SENTINEL = "[codeowl:library-inbox]";

    private static final String LIBRARY_INBOX_DEFAULT_NAME = "Library Inbox";
    private static final String LIBRARY_INBOX_DEFAULT_DESC = "Quick-copied sequences from the Library. "
        + LIBRARY_INBOX_SENTINEL;

    /**
     * Returns the teacher's "Library Inbox" worksheet, creating it on first access.
     *
     * <p>
     * The inbox is identified by a sentinel embedded in the description rather than by name, so renaming the worksheet
     * does not orphan the copy target. If a teacher deletes the inbox, the next one-click copy creates a fresh one.
     *
     * @param ownerId the teacher
     * @return the teacher's Library Inbox
     */
    @Transactional
    public WorksheetDTO getOrCreateDefaultWorksheet(UUID ownerId) {
        if (ownerId == null) {
            throw new DomainValidationException("Owner ID must not be null.");
        }

        Optional<WorksheetDTO> existing = worksheetRepo.findByOwnerId(ownerId).stream()
            .filter(
                w -> w.getDescription() != null
                    && w.getDescription().contains(LIBRARY_INBOX_SENTINEL)
            )
            .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }

        WorksheetDTO fresh = WorksheetDTO.builder()
            .name(LIBRARY_INBOX_DEFAULT_NAME)
            .description(LIBRARY_INBOX_DEFAULT_DESC)
            .ownerId(ownerId)
            .build();
        worksheetRepo.createWorksheet(fresh);
        log.info("Created Library Inbox worksheet {} for owner {}", fresh.getId(), ownerId);
        return fresh;
    }

    /**
     * Adds a sequence to a worksheet.
     *
     * @param worksheetId the worksheet
     * @param sequenceId  the sequence to add
     */
    @Transactional
    public void addSequenceToWorksheet(UUID worksheetId, UUID sequenceId) {
        if (worksheetId == null || sequenceId == null) {
            throw new DomainValidationException("Worksheet and sequence IDs must not be null.");
        }
        WorksheetDTO worksheet = WorksheetDTO.builder().id(worksheetId).build();
        TieredTaskSequenceDTO sequence = TieredTaskSequenceDTO.builder().id(sequenceId).build();
        worksheetRepo.addSequenceToWorksheet(worksheet, sequence);
        log.debug("Linked sequence id={} to worksheet id={}", sequenceId, worksheetId);
    }

    /**
     * Adds several sequences to a worksheet in one transaction.
     *
     * @param worksheetId the worksheet
     * @param sequenceIds the sequences to add; must not be empty
     */
    @Transactional
    public void addSequencesToWorksheet(UUID worksheetId, List<UUID> sequenceIds) {
        if (worksheetId == null) {
            throw new DomainValidationException(WORKSHEET_ID_REQUIRED);
        }
        if (sequenceIds == null || sequenceIds.isEmpty()) {
            throw new DomainValidationException("Sequence list must not be null or empty.");
        }

        WorksheetDTO worksheet = WorksheetDTO.builder().id(worksheetId).build();
        List<TieredTaskSequenceDTO> sequences = sequenceIds.stream()
            .map(id -> TieredTaskSequenceDTO.builder().id(id).build())
            .toList();

        worksheetRepo.addSequencesToWorksheet(worksheet, sequences);
        log.debug("Linked {} sequences to worksheet id={}", sequenceIds.size(), worksheetId);
    }

    /**
     * Removes a sequence from a worksheet.
     *
     * @param worksheetId the worksheet
     * @param sequenceId  the sequence to remove
     */
    @Transactional
    public void removeSequenceFromWorksheet(UUID worksheetId, UUID sequenceId) {
        if (worksheetId == null || sequenceId == null) {
            throw new DomainValidationException("Worksheet and sequence IDs must not be null.");
        }

        WorksheetDTO worksheet = WorksheetDTO.builder().id(worksheetId).build();
        TieredTaskSequenceDTO sequence = TieredTaskSequenceDTO.builder().id(sequenceId).build();
        worksheetRepo.removeSequenceFromWorksheet(worksheet, sequence);
        log.debug("Unlinked sequence id={} from worksheet id={}", sequenceId, worksheetId);
    }

    /**
     * Puts a worksheet's sequences into the given order.
     *
     * @param worksheetId        the worksheet
     * @param orderedSequenceIds every sequence on the worksheet, in the new order
     */
    @Transactional
    public void reorderSequences(UUID worksheetId, List<UUID> orderedSequenceIds) {
        if (worksheetId == null) {
            throw new DomainValidationException(WORKSHEET_ID_REQUIRED);
        }
        if (orderedSequenceIds == null || orderedSequenceIds.isEmpty()) {
            throw new DomainValidationException("Sequence order list must not be null or empty.");
        }
        worksheetRepo.reorderSequences(worksheetId, orderedSequenceIds);
        log.debug("Reordered {} sequences on worksheet id={}", orderedSequenceIds.size(), worksheetId);
    }

    /**
     * Deep-copies a worksheet including all sequences and tasks. The copy gets new UUIDs for everything and the
     * specified newOwnerId.
     *
     * <p>
     * Kept as the single entry point callers already know, but the work now lives in {@link WorksheetCloneService}. It
     * used to be inline here and rebuilt sequences and tasks field by field, which meant every column added afterwards
     * quietly stopped being copied.
     *
     * @param sourceWorksheetId the worksheet to copy
     * @param newOwnerId        the owner of the copy
     * @return the copy
     */
    @Transactional
    public WorksheetDTO copyWorksheet(UUID sourceWorksheetId, UUID newOwnerId) {
        return cloneService.copy(sourceWorksheetId, newOwnerId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Visibility
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Publishes or unpublishes a worksheet.
     *
     * <p>
     * The decision itself lives in {@link WorksheetAccessPolicy}; this method's job is to gather the facts the policy
     * needs — who owns the worksheet, and whether it is the Library Inbox, which is knowledge about a sentinel in the
     * description and therefore not something the domain should have to carry.
     *
     * @param worksheetId the worksheet to change
     * @param visibility  the visibility to set
     * @param actorId     who is asking, or {@code null} when anonymous
     * @param actorRole   their role, or {@code null} when anonymous
     * @return the worksheet as it now stands
     * @throws AccessDeniedException     if the actor may not change this worksheet's visibility
     * @throws DomainValidationException if the id is null or no such worksheet exists
     */
    @Transactional
    public WorksheetDTO setVisibility(
        UUID worksheetId, WorksheetVisibility visibility,
        UUID actorId, UserDTO.UserRole actorRole
    ) {
        if (visibility == null) {
            throw new DomainValidationException("Visibility must not be null.");
        }

        WorksheetDTO worksheet = loadWorksheetById(worksheetId);

        if (
            !WorksheetAccessPolicy.canChangeVisibility(
                worksheet.getOwnerId(), actorId, actorRole, isLibraryInbox(worksheet)
            )
        ) {
            throw new AccessDeniedException(
                "You do not have permission to change this worksheet's visibility"
            );
        }

        worksheetRepo.updateVisibility(worksheetId, visibility);
        worksheet.setVisibility(visibility);
        return worksheet;
    }

    /**
     * Whether this is the auto-created Library Inbox.
     *
     * <p>
     * Identified by the sentinel in the description, the same way {@link #getOrCreateDefaultWorksheet(UUID)} finds it —
     * so renaming the worksheet does not turn it into an ordinary one.
     *
     * @param worksheet the worksheet to check, or null
     * @return true if the worksheet is a Library Inbox
     */
    public static boolean isLibraryInbox(WorksheetDTO worksheet) {
        return worksheet != null
            && worksheet.getDescription() != null
            && worksheet.getDescription().contains(LIBRARY_INBOX_SENTINEL);
    }

    private void validateWorksheetForCreate(WorksheetDTO worksheet) {
        if (worksheet == null) {
            throw new DomainValidationException("Worksheet must not be null.");
        }
        if (isBlank(worksheet.getName())) {
            throw new DomainValidationException("Worksheet name must not be empty.");
        }
        // The description is optional: the create form labels it as such and the
        // worksheets.description column is nullable with a '' default.
    }

    private void validateWorksheetId(WorksheetDTO worksheet) {
        if (worksheet == null || worksheet.getId() == null) {
            throw new DomainValidationException(WORKSHEET_ID_REQUIRED);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
