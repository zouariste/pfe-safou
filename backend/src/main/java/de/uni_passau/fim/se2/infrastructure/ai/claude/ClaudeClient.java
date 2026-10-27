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
package de.uni_passau.fim.se2.infrastructure.ai.claude;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import de.uni_passau.fim.se2.application.service.CodeSanitizerService;
import de.uni_passau.fim.se2.domain.entities.SolveContract;
import de.uni_passau.fim.se2.infrastructure.ai.AbstractAiClient;

@Component
public class ClaudeClient extends AbstractAiClient {

    private static final String NOT_IMPLEMENTED = "ClaudeClient not implemented yet";

    protected ClaudeClient(CodeSanitizerService sanitizer) {
        super(sanitizer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JsonNode solveProblem(final SolveContract contract) throws IOException, InterruptedException {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public JsonNode solveProblemSimple(final String taskDescription, final String language) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JsonNode generateTieredTaskSequence(
        String taskDescription, List<String> concepts, String codeSnippet, String language
    )
        throws IOException, InterruptedException {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProgrammingLanguage(String codeSnippet) throws IOException, InterruptedException {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateTaskDescription(List<String> concepts, String language)
        throws IOException, InterruptedException {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateTaskDescription(String codeSnippet) throws IOException, InterruptedException {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> generateConcepts(String codeSnippet, String language) throws IOException, InterruptedException {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }
}
