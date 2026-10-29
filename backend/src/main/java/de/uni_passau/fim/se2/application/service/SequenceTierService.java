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
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.uni_passau.fim.se2.api.dto.TierCreateRequest;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TaskOriginalDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.TaskOriginalRepository;
import de.uni_passau.fim.se2.domain.interfaces.TaskRepository;
import de.uni_passau.fim.se2.domain.interfaces.TieredTaskSequenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adding a tier to a sequence and taking one out.
 *
 * <p>
 * Tiers stay numbered 1..n without holes: an inserted tier pushes the ones at and above its level up, a deleted one
 * lets the ones above it close the gap. The stored {@code tier_count} follows, since the library filters and facets on
 * it.
 *
 * <p>
 * Deleting is a soft delete. Every table that records what students did on a task references it
 * {@code ON DELETE CASCADE}, so a real delete would erase attempts, hints and research events along with the tier.
 *
 * <p>
 * Owner or admin only. A curator who wants a different set of tiers forks the sequence first, exactly as for any other
 * edit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SequenceTierService {

    /** The most tiers a sequence may have; the same limit manual creation enforces. */
    public static final int MAX_TIERS = 5;

    private final TieredTaskSequenceRepository sequenceRepo;
    private final TaskRepository taskRepo;
    private final TaskOriginalRepository taskOriginalRepo;
    private final OwnershipService ownershipService;
    private final ManualTaskService manualTaskService;

    /**
     * Inserts a teacher-written tier at the requested level.
     *
     * @param sequenceId the sequence to add the tier to
     * @param request    the new tier's statement, code and level
     * @return the new tier, as stored
     * @throws DomainValidationException if the statement or code is blank, the sequence already has {@value #MAX_TIERS}
     *                                   tiers, the level is out of range, or a split is invalid
     */
    @SuppressWarnings("checkstyle:NPathComplexity")
    @Transactional
    public TaskDTO addTier(UUID sequenceId, TierCreateRequest request) {
        if (request == null || request.getProblemStatement() == null || request.getProblemStatement().isBlank()) {
            throw new DomainValidationException("Problem statement must not be empty.");
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new DomainValidationException("Code must not be empty.");
        }

        TieredTaskSequenceDTO sequence = loadOwnedSequence(sequenceId);
        List<TaskDTO> tiers = taskRepo.loadTasksBySequenceId(sequence);

        if (tiers.size() >= MAX_TIERS) {
            throw new DomainValidationException("A sequence can have at most " + MAX_TIERS + " tiers.");
        }
        int level = request.getTierLevel() == null ? tiers.size() + 1 : request.getTierLevel();
        if (level < 1 || level > tiers.size() + 1) {
            throw new DomainValidationException(
                "Tier level must be between 1 and " + (tiers.size() + 1) + "."
            );
        }

        // Built (and its split plan validated) before anything moves, so an invalid tier leaves
        // the sequence exactly as it was even without relying on the rollback.
        TaskDTO tier = manualTaskService.buildTask(
            request.getProblemStatement(), request.getCode(),
            level, request.getSplits(), sequence.getProgrammingLanguage(), sequenceId,
            request.getContextBefore(), request.getContextAfter()
        );
        if (request.getDistractors() != null && !request.getDistractors().isBlank()) {
            tier.setDistractors(request.getDistractors());
        }

        taskRepo.shiftTierLevels(sequenceId, level, +1);
        taskRepo.save(tier);

        // The teacher's own text stands in as the "original". Without it the first edit of this
        // tier would ask how much the generated version needed adjusting, about something nobody
        // generated; the null rating is what tells research a teacher-written tier apart.
        taskOriginalRepo.save(
            TaskOriginalDTO.builder()
                .taskId(tier.getId())
                .originalProblemStatement(tier.getStatement())
                .originalCode(tier.getSolution())
                .adjustmentRating(null)
                .build()
        );

        sequenceRepo.updateTierCount(sequenceId, tiers.size() + 1);
        sequenceRepo.markDiverged(sequenceId);

        log.info("Added tier {} at level {} to sequence {}", tier.getId(), level, sequenceId);

        TaskDTO stored = TaskDTO.builder().id(tier.getId()).build();
        taskRepo.loadById(stored);
        return stored;
    }

    /**
     * Takes a tier out of its sequence and renumbers the ones above it.
     *
     * @param taskId the tier to delete
     * @throws DomainValidationException if the tier is not a current tier of its sequence (for instance, already
     *                                   deleted) or is the only one left
     */
    @Transactional
    public void deleteTier(UUID taskId) {
        if (taskId == null) {
            throw new DomainValidationException("Task ID must not be null.");
        }
        TaskDTO task = TaskDTO.builder().id(taskId).build();
        taskRepo.loadById(task);

        TieredTaskSequenceDTO sequence = loadOwnedSequence(task.getSequenceId());
        List<TaskDTO> tiers = taskRepo.loadTasksBySequenceId(sequence);

        // loadById also finds deleted tasks; the sequence's current tiers do not.
        boolean current = tiers.stream().anyMatch(t -> taskId.equals(t.getId()));
        if (!current) {
            throw new DomainValidationException("This tier has already been deleted.");
        }
        if (tiers.size() <= 1) {
            throw new DomainValidationException("A sequence needs at least one tier.");
        }

        taskRepo.softDelete(taskId);
        taskRepo.shiftTierLevels(sequence.getId(), task.getTierLevel() + 1, -1);
        sequenceRepo.updateTierCount(sequence.getId(), tiers.size() - 1);
        sequenceRepo.markDiverged(sequence.getId());

        log.info("Deleted tier {} (level {}) from sequence {}", taskId, task.getTierLevel(), sequence.getId());
    }

    private TieredTaskSequenceDTO loadOwnedSequence(UUID sequenceId) {
        if (sequenceId == null) {
            throw new DomainValidationException("Sequence ID must not be null.");
        }
        TieredTaskSequenceDTO sequence = TieredTaskSequenceDTO.builder().id(sequenceId).build();
        sequenceRepo.loadById(sequence);
        ownershipService.checkOwnership(sequence.getOwnerId(), "sequence");
        return sequence;
    }
}
