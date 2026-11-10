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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Turns the JSON schemas the services ask the LLM to answer in into {@link JsonNode}s.
 */
final class JsonSchemas {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSchemas() {
    }

    /**
     * Parses a schema written as JSON text.
     *
     * @param json the schema
     * @return the parsed schema
     * @throws IllegalArgumentException if the text is not valid JSON, which is a programming error
     */
    static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON schema", e);
        }
    }
}
