package com.cisternino.domain.strategy;

import com.cisternino.application.mapper.TaskSequenceMapper;
import com.cisternino.domain.entities.GenerationConstraints;
import com.cisternino.domain.entities.TieredTaskSequenceDTO;
import com.cisternino.infrastructure.ai.openai.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;

/**
 * Strategy for generating a task sequence based only on a list of programming
 * concepts. GPT generates a sample task based on the concepts, and then uses
 * both to create the tiered sequence.
 */
public class ConceptOnlyStrategy implements TaskGenerationStrategy {

    /**
     * List of concepts found in code snippet.
     */
    private final List<String> concepts;

    /**
     * The desired programming language.
     */
    private final String language;

    /** Human language for problem statements (e.g. "de", "en"). */
    private final String targetLanguage;

    /**
     * Constructs a new instance of ConceptOnlyStrategy.
     * @param conceptList String representing the code snippet.
     * @param programmingLanguage Language of code snippet.
     * @param targetLanguage Human language for generated statements.
     */
    public ConceptOnlyStrategy(final List<String> conceptList,
                               final String programmingLanguage,
                               final String targetLanguage) {
        this.concepts = conceptList;
        this.language = programmingLanguage;
        this.targetLanguage = targetLanguage;
    }

    /**
     * Generates a tiered task sequence based on the ConceptOnlyStrategy.
     * @param client GhatGPTClient responsible for API connection.
     * @return TieredTaskSequence instance.
     * @throws IOException throws IOException.
     * @throws InterruptedException throws InterruptedException.
     */
    @Override
    public TieredTaskSequenceDTO generate(final OpenAiClient client)
            throws IOException, InterruptedException {
        return generate(client, false);
    }

    @Override
    public TieredTaskSequenceDTO generate(final OpenAiClient client, boolean distractorsEnabled)
            throws IOException, InterruptedException {
        return generate(client, distractorsEnabled, null);
    }

    @Override
    public TieredTaskSequenceDTO generate(final OpenAiClient client, boolean distractorsEnabled,
                                          GenerationConstraints constraints)
            throws IOException, InterruptedException {
        return generate(client, distractorsEnabled, constraints, 3);
    }

    @Override
    public TieredTaskSequenceDTO generate(final OpenAiClient client, boolean distractorsEnabled,
                                          GenerationConstraints constraints, int tierCount)
            throws IOException, InterruptedException {
        String taskDescription = client.generateTaskDescription(concepts, language);
        String codeSnippet = client.solveProblemSimple(taskDescription, language).asText();
        JsonNode json = client.generateTieredTaskSequence(taskDescription,
                concepts, codeSnippet, language, targetLanguage, distractorsEnabled, constraints, tierCount);
        return TaskSequenceMapper.mapToIncompleteSequence(json, concepts,
                language, codeSnippet, taskDescription, tierCount);
    }
}