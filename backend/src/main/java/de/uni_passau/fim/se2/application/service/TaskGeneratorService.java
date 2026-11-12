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

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.uni_passau.fim.se2.domain.entities.GenerationConstraints;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.exceptions.GenerationException;
import de.uni_passau.fim.se2.domain.exceptions.UnsupportedLanguageException;
import de.uni_passau.fim.se2.domain.model.LocLimitWarning;
import de.uni_passau.fim.se2.domain.strategy.TaskGenerationStrategy;
import de.uni_passau.fim.se2.infrastructure.ai.openai.OpenAiClient;

// AP20: constraint-aware generation

/**
 * Coordinates task generation using strategy selection and enrichment pipeline.
 */
@Service
public class TaskGeneratorService {

    private final TaskEnricher taskEnricher;

    private static final Logger log = LoggerFactory.getLogger(TaskGeneratorService.class);

    @Value("${app.version.id:aa09775e-f584-49cd-a0f2-196464f88fa9}")
    private UUID versionId;

    @Value("${app.generation.maxAttempts:3}")
    private int maxAttempts;

    @Value("${app.generation.maxLocPerTask:20}")
    private int maxLocPerTask;

    private final TaskPersistenceService taskPersistenceService;

    /**
     * The ChatGPTClient instance, responsible for Communication with OpenAI API.
     */
    private final OpenAiClient gptClient;

    /**
     * Constructs the TaskGeneratorService instance.
     *
     * @param client The ChatGPTClient instance, responsible for Communication with OpenAI API.
     */
    public TaskGeneratorService(
        final OpenAiClient client, final TaskPersistenceService taskPersistenceService,
        final TaskEnricher taskEnricher
    ) {
        this.gptClient = client;
        this.taskPersistenceService = taskPersistenceService;
        this.taskEnricher = taskEnricher;
    }

    /**
     * Generates a fully enriched tiered task sequence based on available input.
     *
     * @param taskDescription    the provided sample task, or null if none.
     * @param concepts           the provided concepts, or empty/null if none.
     * @param language           the desired programming language.
     * @param codeSnippet        String representing codeSnippet.
     * @param targetLanguage     the human language of the problem statements
     * @param distractorsEnabled whether to generate distractors
     * @return a fully generated TieredTaskSequence with solutions.
     * @throws IOException          GPT interaction failed.
     * @throws InterruptedException Thread interruption.
     */
    public TieredTaskSequenceDTO tryGenerate(
        final String taskDescription, final List<String> concepts,
        final String language,
        final String codeSnippet,
        final String targetLanguage,
        final boolean distractorsEnabled
    ) throws IOException, InterruptedException {
        return tryGenerate(
            taskDescription, concepts, language, codeSnippet, targetLanguage, distractorsEnabled, false, null
        );
    }

    public TieredTaskSequenceDTO tryGenerate(
        final String taskDescription, final List<String> concepts,
        final String language,
        final String codeSnippet,
        final String targetLanguage,
        final boolean distractorsEnabled,
        final boolean contextModeEnabled
    ) throws IOException, InterruptedException {
        return tryGenerate(
            taskDescription, concepts, language, codeSnippet, targetLanguage, distractorsEnabled, contextModeEnabled,
            null
        );
    }

    /**
     * AP20: constraint-aware tryGenerate
     *
     * @param taskDescription    the teacher's sample task description, or null
     * @param concepts           the concepts the sequence should cover, or empty
     * @param language           the programming language of the tasks
     * @param codeSnippet        the teacher's code snippet, or null
     * @param targetLanguage     the human language of the problem statements
     * @param distractorsEnabled whether to generate distractors
     * @param contextModeEnabled whether to mark read-only context lines
     * @param constraints        the teacher's generation constraints, or null
     * @return the generated sequence with solutions, not yet saved
     */
    public TieredTaskSequenceDTO tryGenerate(
        final String taskDescription, final List<String> concepts,
        final String language,
        final String codeSnippet,
        final String targetLanguage,
        final boolean distractorsEnabled,
        final boolean contextModeEnabled,
        final GenerationConstraints constraints
    ) throws IOException, InterruptedException {
        return tryGenerate(
            taskDescription, concepts, language, codeSnippet, targetLanguage,
            distractorsEnabled, contextModeEnabled, constraints, 3, false
        );
    }

    /**
     * AP21: tier-count-aware tryGenerate
     *
     * @param taskDescription    the teacher's sample task description, or null
     * @param concepts           the concepts the sequence should cover, or empty
     * @param language           the programming language of the tasks
     * @param codeSnippet        the teacher's code snippet, or null
     * @param targetLanguage     the human language of the problem statements
     * @param distractorsEnabled whether to generate distractors
     * @param contextModeEnabled whether to mark read-only context lines
     * @param constraints        the teacher's generation constraints, or null
     * @param tierCount          the number of tiers
     * @return the generated sequence with solutions, not yet saved
     */
    public TieredTaskSequenceDTO tryGenerate(
        final String taskDescription, final List<String> concepts,
        final String language,
        final String codeSnippet,
        final String targetLanguage,
        final boolean distractorsEnabled,
        final boolean contextModeEnabled,
        final GenerationConstraints constraints,
        final int tierCount
    ) throws IOException, InterruptedException {
        return tryGenerate(
            taskDescription, concepts, language, codeSnippet, targetLanguage,
            distractorsEnabled, contextModeEnabled, constraints, tierCount, false
        );
    }

    /**
     * AP31: split-aware tryGenerate
     *
     * @param taskDescription    the teacher's sample task description, or null
     * @param concepts           the concepts the sequence should cover, or empty
     * @param language           the programming language of the tasks
     * @param codeSnippet        the teacher's code snippet, or null
     * @param targetLanguage     the human language of the problem statements
     * @param distractorsEnabled whether to generate distractors
     * @param contextModeEnabled whether to mark read-only context lines
     * @param constraints        the teacher's generation constraints, or null
     * @param tierCount          the number of tiers
     * @param splitEnabled       whether to generate inline-split lines
     * @return the generated sequence with solutions, not yet saved
     */
    public TieredTaskSequenceDTO tryGenerate(
        final String taskDescription, final List<String> concepts,
        final String language,
        final String codeSnippet,
        final String targetLanguage,
        final boolean distractorsEnabled,
        final boolean contextModeEnabled,
        final GenerationConstraints constraints,
        final int tierCount,
        final boolean splitEnabled
    ) throws IOException, InterruptedException {

        log.debug(
            "Generating task sequence for language: {}, concepts: {}, targetLanguage: {}, distractors: {}, context: {}, constraints: {}, tierCount: {}",
            language, concepts, targetLanguage, distractorsEnabled, contextModeEnabled,
            constraints != null && constraints.hasAny(), tierCount
        );

        TaskGenerationStrategy strategy = TaskGenerationStrategyFactory
            .choose(taskDescription, codeSnippet, concepts, language, targetLanguage);
        log.trace("Selected strategy: {}", strategy.getClass().getSimpleName());

        TieredTaskSequenceDTO rawSequence = strategy.generate(gptClient, distractorsEnabled, constraints, tierCount);
        rawSequence.setTargetLanguage(targetLanguage);
        rawSequence.setTierCount(tierCount);
        if (constraints != null) {
            rawSequence.setConstraints(constraints);
        }
        log.debug("Generated raw sequence with {} tasks", rawSequence.getTasks().size());

        TieredTaskSequenceDTO enrichedSequence = taskEnricher
            .enrichWithSolutions(rawSequence, distractorsEnabled, contextModeEnabled, splitEnabled);
        log.debug("Enriched sequence with solutions");

        enrichedSequence.setStrategyName(strategy.getClass().getSimpleName());

        log.trace("Successfully generated enriched task sequence (not yet persisted)");
        return enrichedSequence;
    }

    public TieredTaskSequenceDTO generate(
        final String taskDescription, final List<String> concepts,
        final String language, final String codeSnippet,
        final UUID ownerId, final String targetLanguage,
        final boolean distractorsEnabled
    )
        throws IOException, InterruptedException {
        return generate(
            taskDescription, concepts, language, codeSnippet, ownerId, targetLanguage, distractorsEnabled, false, null
        );
    }

    public TieredTaskSequenceDTO generate(
        final String taskDescription, final List<String> concepts,
        final String language, final String codeSnippet,
        final UUID ownerId, final String targetLanguage,
        final boolean distractorsEnabled,
        final boolean contextModeEnabled
    )
        throws IOException, InterruptedException {
        return generate(
            taskDescription, concepts, language, codeSnippet, ownerId, targetLanguage, distractorsEnabled,
            contextModeEnabled, null
        );
    }

    /**
     * AP20: constraint-aware generate with retry loop
     *
     * @param taskDescription    the teacher's sample task description, or null
     * @param concepts           the concepts the sequence should cover, or empty
     * @param language           the programming language of the tasks
     * @param codeSnippet        the teacher's code snippet, or null
     * @param ownerId            the teacher who will own the sequence
     * @param targetLanguage     the human language of the problem statements
     * @param distractorsEnabled whether to generate distractors
     * @param contextModeEnabled whether to mark read-only context lines
     * @param constraints        the teacher's generation constraints, or null
     * @return the generated and saved sequence
     */
    public TieredTaskSequenceDTO generate(
        final String taskDescription, final List<String> concepts,
        final String language, final String codeSnippet,
        final UUID ownerId, final String targetLanguage,
        final boolean distractorsEnabled,
        final boolean contextModeEnabled,
        final GenerationConstraints constraints
    )
        throws IOException, InterruptedException {
        return generate(
            taskDescription, concepts, language, codeSnippet, ownerId, targetLanguage,
            distractorsEnabled, contextModeEnabled, constraints, 3, false
        );
    }

    /**
     * AP21/AP31: tier-count- and split-aware generate with retry loop
     *
     * @param taskDescription    the teacher's sample task description, or null
     * @param concepts           the concepts the sequence should cover, or empty
     * @param language           the programming language of the tasks
     * @param codeSnippet        the teacher's code snippet, or null
     * @param ownerId            the teacher who will own the sequence
     * @param targetLanguage     the human language of the problem statements
     * @param distractorsEnabled whether to generate distractors
     * @param contextModeEnabled whether to mark read-only context lines
     * @param constraints        the teacher's generation constraints, or null
     * @param tierCount          the number of tiers
     * @param splitEnabled       whether to generate inline-split lines
     * @return the generated and saved sequence
     */
    @SuppressWarnings("checkstyle:MethodLength")
    public TieredTaskSequenceDTO generate(
        final String taskDescription, final List<String> concepts,
        final String language, final String codeSnippet,
        final UUID ownerId, final String targetLanguage,
        final boolean distractorsEnabled,
        final boolean contextModeEnabled,
        final GenerationConstraints constraints,
        final int tierCount,
        final boolean splitEnabled
    )
        throws IOException, InterruptedException {

        IOException ioEx = null;
        IllegalStateException validationEx = null;
        GenerationException lastGenEx = null;

        // AP22: When context mode is enabled, allow higher LOC since context lines are read-only
        int effectiveMaxLoc = contextModeEnabled ? 50 : maxLocPerTask;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            try {
                log.info("Attempt {}/{} for task generation (tierCount={})", attempt, maxAttempts, tierCount);

                TieredTaskSequenceDTO seq = tryGenerate(
                    taskDescription, concepts, language, codeSnippet,
                    targetLanguage, distractorsEnabled, contextModeEnabled, constraints, tierCount, splitEnabled
                );

                // Tasks over the line limit are not worth an automatic retry: each one reruns the whole
                // pipeline, which on a slow self-hosted model takes minutes, and the next result may be
                // too long as well. The sequence is saved with a warning and the teacher decides whether
                // to keep it, discard it, or try again.
                warnAboutTooLongTasks(seq, effectiveMaxLoc, attempt);

                seq.setOwnerId(ownerId);
                handlePersistence(seq);
                return seq;

            }
            catch (IllegalStateException e) {
                validationEx = e;
                log.warn("Validation failed on attempt {}: {}", attempt, e.getMessage());
            }
            catch (IOException e) {
                ioEx = e;
                log.warn("IO failure on attempt {}: {}", attempt, e.getMessage());
            }
            catch (UnsupportedLanguageException e) {
                // Not worth a second attempt: the snippet's language does not change between
                // tries, so retrying spends two more LLM calls to fail identically. Rethrown
                // straight away so the teacher gets the real reason instead of the generic
                // "failed after N attempts" this loop ends with.
                log.warn("Unsupported language on attempt {}: {}", attempt, e.getMessage());
                throw e;
            }
            catch (GenerationException e) {
                log.warn("Enrichment failed on attempt {}: {}", attempt, e.getMessage());
                lastGenEx = e;
            }
        }

        // Failed after retries → throw meaningful exception
        if (validationEx != null) {
            throw validationEx;
        }
        if (ioEx != null) {
            throw ioEx;
        }
        // lastGenEx was collected and then dropped on the floor here, so a generation failure
        // surfaced as the bare IllegalStateException below and reached the client as a generic
        // 500 -- GlobalExceptionHandler has a GenerationException handler that never saw one.
        if (lastGenEx != null) {
            throw lastGenEx;
        }

        throw new IllegalStateException("Task generation failed after " + maxAttempts + " attempts");
    }

    /** Attaches a warning to the sequence if any of its tasks is over the line limit. */
    private static void warnAboutTooLongTasks(TieredTaskSequenceDTO seq, int maxLoc, int attempt) {
        List<LocLimitWarning.TaskLoc> tooLong = SequenceValidator.tasksOverLocLimit(seq, maxLoc);
        if (tooLong.isEmpty()) {
            log.info("Generation successful on attempt {}", attempt);
        }
        else {
            log.warn(
                "Generated on attempt {} with tasks over the {}-line limit: {}; leaving the decision to the teacher",
                attempt, maxLoc, tooLong
            );
            seq.setLocWarning(new LocLimitWarning(maxLoc, tooLong));
        }
    }

    private void handlePersistence(TieredTaskSequenceDTO enrichedSequence) {
        log.debug("Persisting sequence with version ID: {}", versionId);
        enrichedSequence.setVersionId(versionId);
        taskPersistenceService.persistSequence(enrichedSequence);
        log.debug("Persisted sequence with version ID: {}", versionId);
    }

}
