package com.cisternino.domain.strategy;

import java.io.IOException;
import java.util.List;

import com.cisternino.application.mapper.TaskSequenceMapper;
import com.cisternino.domain.entities.GenerationConstraints;
import com.cisternino.domain.entities.TieredTaskSequenceDTO;
import com.cisternino.infrastructure.ai.openai.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;

public class CodeOnlyStrategy implements TaskGenerationStrategy {

    /**
     * String representing the sample code snippet.
     */
    private final String codeSnippet;

    /**
     * The desired programming language.
     */
    private String language;

    /** Human language for problem statements (e.g. "de", "en"). */
    private final String targetLanguage;

    /**
     * Constructs a new instance of CodeOnlyStrategy.
     * @param snippet String representing the code snippet.
     * @param programmingLanguage Language of code snippet.
     * @param targetLanguage Human language for generated statements.
     */
    public CodeOnlyStrategy(final String snippet,
                            final String programmingLanguage,
                            final String targetLanguage) {
        this.codeSnippet = snippet;
        this.language = programmingLanguage;
        this.targetLanguage = targetLanguage;
    }

    /**
     * Generates a tiered task sequence based on the CodeOnlyStrategy.
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
        if (language == null || language.isBlank()) {
            language = client.getProgrammingLanguage(codeSnippet);
        }
        String taskDescription = client.generateTaskDescription(codeSnippet);
        List<String> concepts = client.generateConcepts(codeSnippet, language);
        JsonNode json = client.generateTieredTaskSequence(taskDescription,
                concepts, codeSnippet, language, targetLanguage, distractorsEnabled, constraints, tierCount);
        return TaskSequenceMapper.mapToIncompleteSequence(json, concepts,
                language, codeSnippet, taskDescription, tierCount);
    }
}