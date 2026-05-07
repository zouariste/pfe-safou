package com.cisternino.infrastructure.ai.claude;

import com.cisternino.application.service.*;
import com.cisternino.domain.entities.*;
import com.cisternino.infrastructure.ai.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.*;

import java.io.*;
import java.util.*;

@Component
public class ClaudeClient extends AbstractAiClient {

    protected ClaudeClient(CodeSanitizerService sanitizer) {
        super(sanitizer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JsonNode solveProblem(final SolveContract contract) throws IOException, InterruptedException {
        // TODO: echten Aufruf an Claude API (fixes Modell)
        throw new UnsupportedOperationException("ClaudeClient not implemented yet");
    }

    @Override
    public JsonNode solveProblemSimple(final String taskDescription, final String language) {
        // TODO: echten Aufruf an Claude API (fixes Modell)
        throw new UnsupportedOperationException("ClaudeClient not implemented yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JsonNode generateTieredTaskSequence(String taskDescription, List<String> concepts, String codeSnippet, String language)
            throws IOException, InterruptedException {
        throw new UnsupportedOperationException("ClaudeClient not implemented yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProgrammingLanguage(String codeSnippet) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("ClaudeClient not implemented yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateTaskDescription(List<String> concepts, String language) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("ClaudeClient not implemented yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateTaskDescription(String codeSnippet) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("ClaudeClient not implemented yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> generateConcepts(String codeSnippet, String language) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("ClaudeClient not implemented yet");
    }
}