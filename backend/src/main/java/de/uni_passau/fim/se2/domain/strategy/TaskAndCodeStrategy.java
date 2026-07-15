package de.uni_passau.fim.se2.domain.strategy;

import java.io.IOException;
import java.util.List;

import de.uni_passau.fim.se2.application.mapper.TaskSequenceMapper;
import de.uni_passau.fim.se2.domain.entities.GenerationConstraints;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.infrastructure.ai.openai.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;

public class TaskAndCodeStrategy implements TaskGenerationStrategy {

    /**
     * String representing the problem statement of the task.
     */
    private final String taskDescription;

    /**
     * String representing the code snippet.
     */
    private final String codeSnippet;

    /**
     * String representing the desired programming language.
     */
    private String language;

    /** Human language for problem statements (e.g. "de", "en"). */
    private final String targetLanguage;

    /**
     * Constructs a new instance of TaskAndCodeStrategy.
     * @param problemStatement String representing the problem statement.
     * @param snippet String representing the code snippet.
     * @param programmingLanguage String representing the desired language.
     * @param targetLanguage Human language for generated statements.
     */
    public TaskAndCodeStrategy(final String problemStatement,
                               final String snippet,
                               final String programmingLanguage,
                               final String targetLanguage) {
        this.taskDescription = problemStatement;
        this.codeSnippet = snippet;
        this.language = programmingLanguage;
        this.targetLanguage = targetLanguage;
    }

    /**
     * Generates a tiered task sequence based on the TaskAndCodeStrategy.
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
        List<String> concepts = client.generateConcepts(codeSnippet, language);
        JsonNode json = client.generateTieredTaskSequence(taskDescription,
                concepts, codeSnippet, language, targetLanguage, distractorsEnabled, constraints, tierCount);
        return TaskSequenceMapper.mapToIncompleteSequence(json, concepts,
                language, codeSnippet, taskDescription, tierCount);
    }
}