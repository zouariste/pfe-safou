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
package de.uni_passau.fim.se2.domain.entities;

import java.util.List;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Builder
public record SolveContract(
    @NonNull String language,
    @NonNull String taskStatement,
    @NonNull String functionSignature,
    @NonNull String purposeBehavior,
    @NonNull String prePostConditions,
    @Singular("example") @NonNull List<ExamplePair> examples
) {

    private static final String NEWLINE = "\n";

    public SolveContract {
        language = requireText(language, "language");
        taskStatement = requireText(taskStatement, "taskStatement");
        functionSignature = requireText(functionSignature, "functionSignature");
        purposeBehavior = requireText(purposeBehavior, "purposeBehavior");
        prePostConditions = requireText(prePostConditions, "prePostConditions");

        if (examples.isEmpty()) {
            throw new IllegalArgumentException("At least one example is required.");
        }
    }

    private static String requireText(String s, String field) {
        String t = s.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return t;
    }

    public record ExamplePair(String input, String output) {

        public ExamplePair {
            input = (input == null) ? "" : input.trim();
            output = (output == null) ? "" : output.replace("\r\n", NEWLINE);
        }
    }

    @Override
    public @NonNull String toString() {
        StringBuilder result = new StringBuilder("Language: " + language + NEWLINE);
        result.append("Task Statement: ").append(taskStatement).append(NEWLINE);
        result.append("Function Signature: ").append(functionSignature).append(NEWLINE);
        result.append("Purpose Behavior: ").append(purposeBehavior).append(NEWLINE);
        result.append("Pre-Post Conditions: ").append(prePostConditions).append(NEWLINE);
        result.append("Examples:\n");
        for (ExamplePair example : examples) {
            result.append("  Input: ").append(example.input).append(NEWLINE);
            result.append("  Output: ").append(example.output).append(NEWLINE);
        }
        return result.toString();
    }
}
