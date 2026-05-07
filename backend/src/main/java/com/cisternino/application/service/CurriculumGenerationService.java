package com.cisternino.application.service;

import com.cisternino.application.mapper.TaskSequenceMapper;
import com.cisternino.domain.entities.*;
import com.cisternino.domain.interfaces.*;
import com.cisternino.infrastructure.ai.openai.OpenAiClient;
import com.cisternino.domain.exceptions.GenerationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class CurriculumGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(CurriculumGenerationService.class);

    private final CurriculumTopicRepository topicRepo;
    private final CurriculumCodeExampleRepository exampleRepo;
    private final ConceptCurriculumTopicRepository conceptTopicRepo;
    private final OpenAiClient aiClient;
    private final TaskEnricher taskEnricher;

    @Value("${app.version.id:aa09775e-f584-49cd-a0f2-196464f88fa9}")
    private UUID versionId;

    @Value("${app.generation.maxAttempts:3}")
    private int maxAttempts;

    public CurriculumGenerationService(CurriculumTopicRepository topicRepo,
                                       CurriculumCodeExampleRepository exampleRepo,
                                       ConceptCurriculumTopicRepository conceptTopicRepo,
                                       OpenAiClient aiClient,
                                       TaskEnricher taskEnricher) {
        this.topicRepo = topicRepo;
        this.exampleRepo = exampleRepo;
        this.conceptTopicRepo = conceptTopicRepo;
        this.aiClient = aiClient;
        this.taskEnricher = taskEnricher;
    }

    /**
     * Generates a tiered task sequence based on a curriculum topic.
     * Selects seed code examples and concept mappings from the DB, then
     * delegates to the OpenAiClient with the dedicated curriculum prompt.
     */
    public TieredTaskSequenceDTO generate(UUID topicId, String language, String targetLanguage,
                                          int tierCount, boolean distractorsEnabled,
                                          GenerationConstraints constraints, UUID ownerId)
            throws IOException, InterruptedException {

        CurriculumTopicDTO topic = topicRepo.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Curriculum topic not found: " + topicId));

        // Get seed code examples (prefer matching language, fallback to all)
        List<CurriculumCodeExampleDTO> examples = exampleRepo.findByTopicIdAndLanguage(topicId, language);
        if (examples.isEmpty()) {
            examples = exampleRepo.findByTopicId(topicId);
        }
        if (examples.isEmpty()) {
            throw new IllegalStateException("No code examples found for curriculum topic: " + topic.getName());
        }
        // Limit to 3 seed examples
        if (examples.size() > 3) {
            examples = examples.subList(0, 3);
        }

        // Get concepts mapped to this topic
        List<ConceptDTO> conceptDTOs = conceptTopicRepo.findAllByCurriculumTopic(topic);
        List<String> concepts = conceptDTOs.stream()
                .map(ConceptDTO::getName)
                .collect(Collectors.toList());
        if (concepts.isEmpty()) {
            concepts = List.of("general-programming");
        }

        logger.info("Curriculum generation: topic='{}', grade={}, language={}, tierCount={}, examples={}, concepts={}",
                topic.getName(), topic.getGradeLevel(), language, tierCount, examples.size(), concepts);

        // Retry-loop analog to TaskGeneratorService — robust against transient LLM failures
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return tryGenerateOnce(topic, topicId, examples, concepts, language, targetLanguage,
                                       tierCount, distractorsEnabled, constraints, ownerId);
            } catch (IOException | RuntimeException e) {
                lastError = e;
                logger.warn("Curriculum generation attempt {}/{} failed: {}", attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    // Exponential backoff with jitter
                    long delay = (long) Math.min(30_000L,
                            Math.pow(2, attempt) * 1000L * (0.5 + ThreadLocalRandom.current().nextDouble(0.5)));
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                }
            }
        }
        throw new GenerationException("Curriculum generation failed after " + maxAttempts + " attempts", lastError);
    }

    private TieredTaskSequenceDTO tryGenerateOnce(CurriculumTopicDTO topic, UUID topicId,
                                                  List<CurriculumCodeExampleDTO> examples,
                                                  List<String> concepts, String language,
                                                  String targetLanguage, int tierCount,
                                                  boolean distractorsEnabled,
                                                  GenerationConstraints constraints, UUID ownerId)
            throws IOException, InterruptedException {
        // Generate via OpenAI with curriculum-specific prompt
        JsonNode json = aiClient.generateCurriculumSequence(
                topic, examples, concepts, language, targetLanguage,
                tierCount, distractorsEnabled, constraints);

        // Map to DTO
        TieredTaskSequenceDTO sequence = TaskSequenceMapper.mapToIncompleteSequence(
                json, concepts, language, null, topic.getName(), tierCount);

        // Set metadata
        sequence.setOwnerId(ownerId);
        sequence.setVersionId(versionId);
        sequence.setConstraints(constraints);
        sequence.setCurriculumTopicId(topicId);

        // Enrich tasks (complexity analysis, distractors, etc.)
        TieredTaskSequenceDTO enriched = taskEnricher.enrichWithSolutions(sequence, distractorsEnabled);
        // Preserve metadata that enrichWithSolutions does not copy
        enriched.setOwnerId(ownerId);
        enriched.setVersionId(versionId);
        enriched.setConstraints(constraints);
        enriched.setCurriculumTopicId(topicId);
        enriched.setTargetLanguage(targetLanguage);
        enriched.setTierCount(tierCount);
        return enriched;
    }

    /** Returns all top-level curriculum topics (Jahrgangsstufen). */
    public List<CurriculumTopicDTO> getTopLevelTopics() {
        return topicRepo.findTopLevel();
    }

    /** Returns sub-topics for a given parent topic. */
    public List<CurriculumTopicDTO> getSubTopics(UUID parentId) {
        return topicRepo.findByParentId(parentId);
    }

    /** Returns code examples for a given topic and language. */
    public List<CurriculumCodeExampleDTO> getExamples(UUID topicId, String language) {
        if (language != null && !language.isBlank()) {
            return exampleRepo.findByTopicIdAndLanguage(topicId, language);
        }
        return exampleRepo.findByTopicId(topicId);
    }
}
