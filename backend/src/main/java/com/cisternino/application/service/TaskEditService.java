package com.cisternino.application.service;

import com.cisternino.api.dto.TaskEditRequest;
import com.cisternino.domain.entities.TaskDTO;
import com.cisternino.domain.entities.TaskOriginalDTO;
import com.cisternino.domain.exceptions.DomainValidationException;
import com.cisternino.domain.interfaces.TaskOriginalRepository;
import com.cisternino.domain.interfaces.TaskRepository;
import com.cisternino.domain.interfaces.TieredTaskSequenceRepository;
import com.cisternino.application.service.analysis.ChangeAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for editing tasks (AP9 — Task-Bearbeitung & Versionierung).
 * On the first edit, the original version is saved to task_originals.
 * Subsequent edits update the task directly without creating another original.
 */
@Service
public class TaskEditService {

    private static final Logger logger = LoggerFactory.getLogger(TaskEditService.class);

    private final TaskRepository taskRepo;
    private final TaskOriginalRepository taskOriginalRepo;
    private final TieredTaskSequenceRepository sequenceRepo;
    private final OwnershipService ownershipService;
    private final ChangeAnalysisService changeAnalysisService;

    public TaskEditService(TaskRepository taskRepo,
                           TaskOriginalRepository taskOriginalRepo,
                           TieredTaskSequenceRepository sequenceRepo,
                           OwnershipService ownershipService,
                           ChangeAnalysisService changeAnalysisService) {
        this.taskRepo = taskRepo;
        this.taskOriginalRepo = taskOriginalRepo;
        this.sequenceRepo = sequenceRepo;
        this.ownershipService = ownershipService;
        this.changeAnalysisService = changeAnalysisService;
    }

    /**
     * Edits a task's problem statement and/or code.
     * <ol>
     *   <li>Loads the current task</li>
     *   <li>Checks ownership (task -> sequence -> owner_id) or admin</li>
     *   <li>If no task_original exists yet, saves the current version as original</li>
     *   <li>Updates the task with new values</li>
     * </ol>
     *
     * @param taskId  the task to edit
     * @param request the edit payload
     * @return the updated TaskDTO
     */
    @Transactional
    public TaskDTO editTask(UUID taskId, TaskEditRequest request) {
        if (taskId == null) {
            throw new DomainValidationException("Task ID must not be null.");
        }
        if (request.getProblemStatement() == null || request.getProblemStatement().isBlank()) {
            throw new DomainValidationException("Problem statement must not be empty.");
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new DomainValidationException("Code must not be empty.");
        }

        // 1. Load current task
        TaskDTO task = new TaskDTO();
        task.setId(taskId);
        taskRepo.loadById(task);

        // 2. Check ownership via sequence -> owner_id
        UUID ownerId = sequenceRepo.findOwnerIdByTaskId(taskId);
        ownershipService.checkOwnership(ownerId, "task");

        // 3. If first edit, save original before overwriting
        boolean isFirstEdit = !taskOriginalRepo.existsByTaskId(taskId);
        if (isFirstEdit) {
            if (request.getAdjustmentRating() == null) {
                throw new DomainValidationException(
                        "adjustmentRating is required on the first edit (1-5).");
            }
            if (request.getAdjustmentRating() < 1 || request.getAdjustmentRating() > 5) {
                throw new DomainValidationException(
                        "adjustmentRating must be between 1 and 5.");
            }

            TaskOriginalDTO original = TaskOriginalDTO.builder()
                    .taskId(taskId)
                    .originalProblemStatement(task.getStatement())
                    .originalCode(task.getSolution())
                    .adjustmentRating(request.getAdjustmentRating())
                    .build();
            taskOriginalRepo.save(original);
            logger.info("Saved original for task {} (rating={})", taskId, request.getAdjustmentRating());
        }

        // 4. Update the task
        taskRepo.updateStatementAndSolution(taskId, request.getProblemStatement(), request.getCode());

        // AP15: Update distractors if provided in the request
        if (request.getDistractors() != null) {
            String distractors = request.getDistractors().isBlank() ? null : request.getDistractors();
            taskRepo.updateDistractors(taskId, distractors);
        }

        logger.info("Updated task {} (firstEdit={})", taskId, isFirstEdit);

        // 5. Run change analysis automatically
        try {
            changeAnalysisService.analyzeTask(taskId);
            logger.info("Change analysis completed for task {}", taskId);
        } catch (Exception e) {
            logger.warn("Change analysis failed for task {} (non-blocking): {}", taskId, e.getMessage());
        }

        // Reload and return
        TaskDTO updated = new TaskDTO();
        updated.setId(taskId);
        taskRepo.loadById(updated);
        return updated;
    }
}
