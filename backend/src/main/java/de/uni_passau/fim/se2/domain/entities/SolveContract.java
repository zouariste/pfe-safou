package de.uni_passau.fim.se2.domain.entities;

import lombok.*;
import lombok.extern.jackson.*;

import java.util.*;

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
    public SolveContract {
        language          = requireText(language, "language");
        taskStatement     = requireText(taskStatement, "taskStatement");
        functionSignature = requireText(functionSignature, "functionSignature");
        purposeBehavior   = requireText(purposeBehavior, "purposeBehavior");
        prePostConditions = requireText(prePostConditions, "prePostConditions");

        if (examples.isEmpty()) {
            throw new IllegalArgumentException("At least one example is required.");
        }
    }

    private static String requireText(String s, String field) {
        String t = s.trim();
        if (t.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return t;
    }

    public record ExamplePair(String input, String output) {
        public ExamplePair {
            input = (input == null) ? "" : input.trim();
            output = (output == null) ? "" : output.replace("\r\n", "\n");
        }
    }

    @Override
    public @NonNull String toString(){
        StringBuilder result = new StringBuilder("Language: " + language + "\n");
        result.append("Task Statement: ").append(taskStatement).append("\n");
        result.append("Function Signature: ").append(functionSignature).append("\n");
        result.append("Purpose Behavior: ").append(purposeBehavior).append("\n");
        result.append("Pre-Post Conditions: ").append(prePostConditions).append("\n");
        result.append("Examples:\n");
        for (ExamplePair example : examples) {
            result.append("  Input: ").append(example.input).append("\n");
            result.append("  Output: ").append(example.output).append("\n");
        }
        return result.toString();
    }
}