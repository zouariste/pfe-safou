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
package de.uni_passau.fim.se2.domain.interfaces;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import de.uni_passau.fim.se2.domain.entities.GenerationConstraints;
import de.uni_passau.fim.se2.domain.entities.SolveContract;

public interface AiModelClient {

    /**
     * Sends a single problem statement to the provider and returns a code-only solution.
     *
     * <p>
     * The prompt strictly instructs the provider to return only properly formatted code, without any explanation,
     * comments, or extra output.
     *
     * @param contract the task statement, signature, behaviour, conditions and examples the solution must satisfy
     * @return a {@link JsonNode} containing one field "code" with the solution
     * @throws IOException          if API communication fails
     * @throws InterruptedException if the thread is interrupted during the request
     */
    JsonNode solveProblem(SolveContract contract) throws IOException, InterruptedException;

    JsonNode solveProblemSimple(String taskDescription, String language)
        throws IOException, InterruptedException;

    /**
     * Generates a TieredTaskSequence.
     *
     * @param taskDescription The problem statement of the sample task.
     * @param concepts        Concepts found in the sample task.
     * @param codeSnippet     Code snippet serving as a solution to the sample task.
     * @param language        The programming language of the code snippet.
     * @return JsonNode representing the TieredTaskSequence.
     * @throws IOException          throws IOException.
     * @throws InterruptedException throws InterruptedException.
     */
    JsonNode generateTieredTaskSequence(
        String taskDescription,
        List<String> concepts,
        String codeSnippet,
        String language
    ) throws IOException, InterruptedException;

    /**
     * Generates a TieredTaskSequence with a specified target language for problem statements.
     *
     * @param targetLanguage  Human language code for statements (e.g. "de", "en").
     * @param taskDescription the teacher's sample task description, or null
     * @param concepts        the concepts the sequence should cover
     * @param codeSnippet     the teacher's code snippet, or null
     * @param language        the programming language of the tasks
     * @return the model's response describing the tiered tasks
     */
    default JsonNode generateTieredTaskSequence(
        final String taskDescription,
        final List<String> concepts,
        final String codeSnippet,
        final String language,
        final String targetLanguage
    ) throws IOException, InterruptedException {
        return generateTieredTaskSequence(taskDescription, concepts, codeSnippet, language);
    }

    /**
     * Generates a problem statement for a given code snippet using a provided prompt body. Default implementation falls
     * back to the single-argument variant, so existing clients keep working until they implement the override.
     *
     * @param codeSnippet    the code to describe
     * @param userPromptBody the prompt to send with the code
     * @return the generated problem statement
     */
    default String generateTaskDescription(final String codeSnippet, final String userPromptBody)
        throws IOException, InterruptedException {
        return generateTaskDescription(codeSnippet);
    }

    /**
     * Generate using explicit system prompt + user prompt/body. Providers that support roles should use both; others
     * may concatenate.
     *
     * @param codeSnippet    the code to describe
     * @param systemPrompt   the system prompt to send
     * @param userPromptBody the user prompt to send with the code
     * @return the generated problem statement
     */
    default String generateTaskDescription(
        final String codeSnippet,
        final String systemPrompt,
        final String userPromptBody
    )
        throws IOException, InterruptedException {
        // Fallback: concatenate if provider lacks role support
        String merged = (systemPrompt == null || systemPrompt.isBlank())
            ? userPromptBody
            : ("SYSTEM:\n" + systemPrompt + "\n\nUSER:\n" + userPromptBody);
        return generateTaskDescription(codeSnippet, merged);
    }

    /**
     * Returns the programming language of the {@param codeSnippet}.
     *
     * @param codeSnippet String containing the code snippet to be analyzed.
     * @return String representing the language detected.
     * @throws IOException          throws IOException.
     * @throws InterruptedException throws InterruptedException.
     */
    String getProgrammingLanguage(String codeSnippet)
        throws IOException, InterruptedException;

    /**
     * Generates a problem statement fitting to the {@param concepts}.
     *
     * @param concepts List of concepts to be included in the taskDescription.
     * @param language Desired programming language.
     * @return String containing the problem statement.
     * @throws IOException          throws IOException.
     * @throws InterruptedException throws InterruptedException.
     */
    String generateTaskDescription(
        List<String> concepts,
        String language
    )
        throws IOException, InterruptedException;

    /**
     * Generates a problem statement fitting to the {@param codeSnippet}.
     *
     * @param codeSnippet String representing the codeSnippet to be analyzed.
     * @return String containing the problem statement.
     * @throws IOException          throws IOException
     * @throws InterruptedException throws InterruptedException
     */
    String generateTaskDescription(String codeSnippet)
        throws IOException, InterruptedException;

    /**
     * Generates a List of programming concepts contained in {@param codeSnippet}, constrained to the fixed concept
     * catalog (AP25).
     *
     * @param codeSnippet String representing the codeSnippet to be analyzed.
     * @param language    Programming language of the snippet (e.g. "java", "python").
     * @return List of normalized concept slugs from the catalog.
     * @throws IOException          throws IOException.
     * @throws InterruptedException throws InterruptedException.
     */
    List<String> generateConcepts(String codeSnippet, String language)
        throws IOException, InterruptedException;

    /**
     * Generates distractor lines for a given solution code (AP15 Antagonist-Call).
     *
     * @param solutionCode   the correct solution
     * @param language       programming language
     * @param taskStatement  the task description
     * @param tierLevel      difficulty tier (1-3)
     * @param distractorPlan JSON string with type and rationale, or null
     * @param count          number of distractors to generate
     * @return list of distractor line strings (each ending with #distractor)
     */
    default List<String> generateDistractors(
        String solutionCode, String language,
        String taskStatement, int tierLevel,
        String distractorPlan, int count
    )
        throws IOException, InterruptedException {
        return List.of();
    }

    /**
     * AP19 Teil A: Generates semantic topic tags for a sequence. The response is the tagger's JSON object
     * ({@code primary_tags}, {@code aliases} — each a list of {@code {"de","en"}} slug pairs — and
     * {@code short_description}), as a string.
     *
     * @param systemPrompt the tagger identity ({@code SequenceSemanticTagger})
     * @param userPrompt   the sequence's combined statements and code
     * @return the JSON object as a string, or {@code null} if the provider does not support it
     */
    default String generateSemanticTags(String systemPrompt, String userPrompt)
        throws IOException, InterruptedException {
        return null;
    }

    /**
     * AP31: Generates an inline-split plan for a (solved) task — one complex line decomposed into 2–4 fragments with
     * optional display prefix/suffix and fragment distractors.
     *
     * @param solutionCode  the task's solution
     * @param language      the programming language of the solution
     * @param taskStatement the task's problem statement
     * @param tierLevel     the task's tier
     * @return split_plan JSON string (object with a "splits" array), or {@code null}.
     */
    default String generateSplitPlan(
        String solutionCode, String language,
        String taskStatement, int tierLevel
    )
        throws IOException, InterruptedException {
        return null;
    }

    /**
     * AP31: Second pass — generates ONE fragment-level distractor for an already-decided split so the wrong fragment is
     * a drop-in for a single slot (shape-matched to one of {@code fragments}).
     *
     * @param fragments     the correct fragments of the split line, in order
     * @param prefix        the fixed text before the fragments
     * @param suffix        the fixed text after the fragments
     * @param language      the programming language of the line
     * @param taskStatement the task's problem statement
     * @param tierLevel     the task's tier
     * @return JSON {@code {"fragmentIndex":int,"text":"...","type":"..."}}, or {@code null} if none fits.
     */
    default String generateSplitDistractor(
        List<String> fragments, String prefix, String suffix,
        String language, String taskStatement, int tierLevel
    )
        throws IOException, InterruptedException {
        return null;
    }

    /**
     * Generates a TieredTaskSequence with optional distractor planning (AP15).
     *
     * @param taskDescription    the teacher's sample task description, or null
     * @param concepts           the concepts the sequence should cover
     * @param codeSnippet        the teacher's code snippet, or null
     * @param language           the programming language of the tasks
     * @param targetLanguage     the human language of the problem statements
     * @param distractorsEnabled whether to plan distractors
     * @return the model's response describing the tiered tasks
     */
    default JsonNode generateTieredTaskSequence(
        final String taskDescription,
        final List<String> concepts,
        final String codeSnippet,
        final String language,
        final String targetLanguage,
        boolean distractorsEnabled
    )
        throws IOException, InterruptedException {
        return generateTieredTaskSequence(taskDescription, concepts, codeSnippet, language, targetLanguage);
    }

    /**
     * AP20: Generates a TieredTaskSequence with constraints + distractors.
     *
     * @param taskDescription    the teacher's sample task description, or null
     * @param concepts           the concepts the sequence should cover
     * @param codeSnippet        the teacher's code snippet, or null
     * @param language           the programming language of the tasks
     * @param targetLanguage     the human language of the problem statements
     * @param distractorsEnabled whether to plan distractors
     * @param constraints        the teacher's generation constraints, or null
     * @return the model's response describing the tiered tasks
     */
    default JsonNode generateTieredTaskSequence(
        final String taskDescription,
        final List<String> concepts,
        final String codeSnippet,
        final String language,
        final String targetLanguage,
        boolean distractorsEnabled,
        GenerationConstraints constraints
    )
        throws IOException, InterruptedException {
        return generateTieredTaskSequence(
            taskDescription, concepts, codeSnippet, language, targetLanguage, distractorsEnabled
        );
    }

    /**
     * AP21: Generates a TieredTaskSequence with constraints + distractors + tierCount.
     *
     * @param taskDescription    the teacher's sample task description, or null
     * @param concepts           the concepts the sequence should cover
     * @param codeSnippet        the teacher's code snippet, or null
     * @param language           the programming language of the tasks
     * @param targetLanguage     the human language of the problem statements
     * @param distractorsEnabled whether to plan distractors
     * @param constraints        the teacher's generation constraints, or null
     * @param tierCount          the number of tiers
     * @return the model's response describing the tiered tasks
     */
    default JsonNode generateTieredTaskSequence(
        final String taskDescription,
        final List<String> concepts,
        final String codeSnippet,
        final String language,
        final String targetLanguage,
        boolean distractorsEnabled,
        GenerationConstraints constraints,
        int tierCount
    )
        throws IOException, InterruptedException {
        return generateTieredTaskSequence(
            taskDescription, concepts, codeSnippet, language, targetLanguage, distractorsEnabled, constraints
        );
    }
}
