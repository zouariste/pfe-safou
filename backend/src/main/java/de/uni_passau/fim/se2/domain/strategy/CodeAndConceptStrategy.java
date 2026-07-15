package de.uni_passau.fim.se2.domain.strategy;

import java.io.IOException;
import java.util.List;

import de.uni_passau.fim.se2.application.mapper.TaskSequenceMapper;
import de.uni_passau.fim.se2.domain.entities.GenerationConstraints;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.infrastructure.ai.openai.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;

public class CodeAndConceptStrategy implements TaskGenerationStrategy {

    /**
     * String representing the sample code snippet.
     */
    private final String codeSnippet;

    /**
     * List of concepts found in code snippet.
     */
    private final List<String> concepts;

    /**
     * The desired programming language.
     */
    private String language;

    /** Human language for problem statements (e.g. "de", "en"). */
    private final String targetLanguage;

    /**
     * Constructs a new instance of CodeAndConceptStrategy.
     * @param snippet String representing the code snippet.
     * @param concept List of concepts found in code snippet.
     * @param programmingLanguage Language of code snippet.
     * @param targetLanguage Human language for generated statements.
     */
    public CodeAndConceptStrategy(final String snippet,
                                  final List<String> concept,
                                  final String programmingLanguage,
                                  final String targetLanguage) {
        this.codeSnippet = snippet;
        this.concepts = concept;
        this.language = programmingLanguage;
        this.targetLanguage = targetLanguage;
    }

    /**
     * Generates a tiered task sequence based on the CodeAndConceptStrategy.
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
        String taskDescription = client.generateTaskDescription(codeSnippet);
        if (language == null || language.isBlank()) {
            language = client.getProgrammingLanguage(codeSnippet);
        }
        JsonNode json = client.generateTieredTaskSequence(taskDescription,
                concepts, codeSnippet, language, targetLanguage, distractorsEnabled, constraints, tierCount);
        return TaskSequenceMapper.mapToIncompleteSequence(json, concepts,
                language, codeSnippet, taskDescription, tierCount);
    }
}