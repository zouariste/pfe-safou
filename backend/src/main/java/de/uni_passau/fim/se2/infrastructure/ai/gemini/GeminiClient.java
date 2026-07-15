package de.uni_passau.fim.se2.infrastructure.ai.gemini;

import de.uni_passau.fim.se2.application.service.*;
import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.infrastructure.ai.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.*;

import java.io.*;
import java.util.*;

@Component
public class GeminiClient extends AbstractAiClient {

    protected GeminiClient(CodeSanitizerService sanitizer) {
        super(sanitizer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JsonNode solveProblem(final SolveContract contract) throws IOException, InterruptedException {
        // TODO: echten Aufruf an Gemini API (fixes Modell)
        throw new UnsupportedOperationException("GeminiClient not implemented yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JsonNode generateTieredTaskSequence(String taskDescription, List<String> concepts, String codeSnippet, String language)
            throws IOException, InterruptedException {
        throw new UnsupportedOperationException("GeminiClient not implemented yet");
    }

    @Override
    public JsonNode solveProblemSimple(final String taskDescription, final String language) {
        // TODO: echten Aufruf an Claude API (fixes Modell)
        throw new UnsupportedOperationException("Gemini not implemented yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProgrammingLanguage(String codeSnippet) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("GeminiClient not implemented yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateTaskDescription(List<String> concepts, String language) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("GeminiClient not implemented yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateTaskDescription(String codeSnippet) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("GeminiClient not implemented yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> generateConcepts(String codeSnippet, String language) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("GeminiClient not implemented yet");
    }
}