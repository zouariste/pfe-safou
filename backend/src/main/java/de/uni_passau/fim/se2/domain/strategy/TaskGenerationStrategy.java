package de.uni_passau.fim.se2.domain.strategy;

import de.uni_passau.fim.se2.domain.entities.GenerationConstraints;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.infrastructure.ai.openai.OpenAiClient;

import java.io.IOException;

/**
 * Strategy interface for generating tiered task sequences.
 */
public interface TaskGenerationStrategy {

    /**
     * Generates a Tiered Task Sequence based on the Strategy.
     * @param client The ChatGPTCLient responsible for API communication.
     * @return a TieredTaskSequence instance.
     * @throws IOException throws IOException.
     * @throws InterruptedException throws InterruptedException.
     */
    TieredTaskSequenceDTO generate(OpenAiClient client)
            throws IOException, InterruptedException;

    /**
     * Generates a Tiered Task Sequence with optional distractor planning (AP15).
     * Default delegates to the original method (ignoring the flag).
     */
    default TieredTaskSequenceDTO generate(OpenAiClient client, boolean distractorsEnabled)
            throws IOException, InterruptedException {
        return generate(client);
    }

    /**
     * AP20: Generates a Tiered Task Sequence with constraints + distractors.
     * Default delegates to the distractor-aware overload (ignoring constraints).
     */
    default TieredTaskSequenceDTO generate(OpenAiClient client, boolean distractorsEnabled,
                                           GenerationConstraints constraints)
            throws IOException, InterruptedException {
        return generate(client, distractorsEnabled);
    }

    /**
     * AP21: Generates a Tiered Task Sequence with constraints + distractors + tierCount.
     * Default delegates to the constraint-aware overload (ignoring tierCount).
     */
    default TieredTaskSequenceDTO generate(OpenAiClient client, boolean distractorsEnabled,
                                           GenerationConstraints constraints, int tierCount)
            throws IOException, InterruptedException {
        return generate(client, distractorsEnabled, constraints);
    }
}