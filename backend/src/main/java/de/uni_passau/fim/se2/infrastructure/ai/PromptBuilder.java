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
package de.uni_passau.fim.se2.infrastructure.ai;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.uni_passau.fim.se2.domain.entities.CurriculumCodeExampleDTO;
import de.uni_passau.fim.se2.domain.entities.CurriculumTopicDTO;
import de.uni_passau.fim.se2.domain.entities.GenerationConstraints;
import de.uni_passau.fim.se2.domain.entities.SolveContract;
import de.uni_passau.fim.se2.domain.model.DistractorStrategy;
import de.uni_passau.fim.se2.domain.model.SupportedLanguage;

/**
 * Responsible for constructing structured GPT request payloads, including appropriate system and user messages and
 * strict JSON response validation schemas.
 */
@Component
public class PromptBuilder {

    private static final String LIST_SEPARATOR = ", ";
    private static final String ROLE_USER = "user";
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE = "role";
    private static final String CONTENT = "content";
    static final String DISTRACTOR_TAXONOMY_PLACEHOLDER = "{{DISTRACTOR_TAXONOMY}}";
    static final String DISTRACTOR_TIER_MAPPING_PLACEHOLDER = "{{DISTRACTOR_TIER_MAPPING}}";
    private static final String MESSAGES = "messages";
    private static final String RESPONSE_FORMAT = "response_format";
    private static final String JSON_SCHEMA = "json_schema";
    private static final String TYPE = "type";
    private static final String NAME = "name";
    private static final String STRICT = "strict";
    private static final String OBJECT = "object";
    private static final String ARRAY = "array";
    private static final String MIN_ITEMS = "minItems";
    private static final String MAX_ITEMS = "maxItems";
    private static final String TIER = "tier";
    private static final String STATEMENT = "statement";
    private static final String LANGUAGE = "language";
    private static final String TASK_STATEMENT = "taskStatement";
    private static final String FUNCTION_SIGNATURE = "functionSignature";
    private static final String PURPOSE_BEHAVIOR = "purposeBehavior";
    private static final String PRE_POST_CONDITIONS = "prePostConditions";
    private static final String INPUT = "input";
    private static final String OUTPUT = "output";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String ITEMS = "items";
    private static final String EXAMPLES = "examples";
    private static final String CONTRACT = "contract";
    private static final String TASKS = "tasks";
    private static final String SCHEMA = "schema";
    private static final String STRING = "string";
    private static final String ENGLISH = "en";
    private static final String RATIONALE = "rationale";

    /**
     * Object mapper used for constructing prompts.
     */
    private final ObjectMapper mapper;

    /**
     * An instance of {@link IdentityLoader} used to load identity prompt resources required for constructing GPT
     * requests.
     */
    private final IdentityLoader identityLoader;

    /**
     * Constructs a new PromptBuilder instance.
     *
     * @param objectMapper the objectMapper used for building prompts.
     */
    public PromptBuilder(final ObjectMapper objectMapper, final IdentityLoader identityLoader) {
        this.mapper = objectMapper;
        this.identityLoader = identityLoader;
    }

    /**
     * Constructs a GPT request to generate a tiered task sequence based on task description, concepts, reference code,
     * and target language.
     *
     * @param taskDescription a plain English description of the task
     * @param concepts        the core programming concepts involved
     * @param codeSnippet     teacher-provided code snippet
     * @param language        programming language to use
     * @return GPT-compliant JSON request object
     */
    public ObjectNode buildSequenceRequest(
        final String taskDescription, final List<String> concepts,
        final String codeSnippet, final String language
    ) {
        return buildSequenceRequest(taskDescription, concepts, codeSnippet, language, null);
    }

    /**
     * Builds the request for a tiered task sequence, with the problem statements in the given language.
     *
     * @param taskDescription the teacher's sample task description, or null
     * @param concepts        the concepts the sequence should cover
     * @param codeSnippet     the teacher's code snippet, or null
     * @param language        the programming language of the tasks
     * @param targetLanguage  the human language of the problem statements, or null for the default
     * @return the chat completions request
     */
    @SuppressWarnings("checkstyle:MethodLength")
    public ObjectNode buildSequenceRequest(
        final String taskDescription, final List<String> concepts,
        final String codeSnippet, final String language,
        final String targetLanguage
    ) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_SYSTEM)
                .put(CONTENT, sequenceIdentity("Sequence", 3))
        );

        // Resolve human language label for the prompt instruction
        String langLabel;
        if (ENGLISH.equalsIgnoreCase(targetLanguage)) {
            langLabel = "English";
        }
        else {
            langLabel = "German";   // default
        }

        String targetLanguageInstruction = """

            IMPORTANT — Human Language Requirement:
            All student-facing text (statement, contract.taskStatement, contract.purposeBehavior,
            contract.prePostConditions) MUST be written in %s.
            Code, function signatures, variable names, and concept names remain in English.
            """.formatted(langLabel);

        String prompt = """
            Generate exactly 3 tiered programming tasks (tiers 1–3) for the inputs below.
            Increase complexity across tiers. Statements must be student-facing (one sentence).

            Inputs:
            - Task Description: %s
            - Programming Language: %s
            - Programming Concepts: %s
            - Reference Code Snippet:
            %s

            Output (STRICT):
            Return ONLY JSON matching the provided response schema.
            For each task:
            - Provide: tier, statement, and contract.
            - contract must include ALL fields: language, taskStatement, functionSignature,
              purposeBehavior, prePostConditions, examples[1..2] with {input,output}.
            - statement MUST equal contract.taskStatement exactly.
            Examples must be concrete minimal invocation/result pairs appropriate to the functionSignature.
            No placeholders, no empty strings for any field. If a field cannot be filled logically put ‘none’.

            Use the reference snippet only as conceptual context, not as a structural template.
            The generated tasks’ intended solutions must consist of a single function only, not full programs.
            %s""".formatted(
            taskDescription, language, String.join(LIST_SEPARATOR, concepts), codeSnippet,
            targetLanguageInstruction
        );

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_USER)
                .put(CONTENT, prompt)
        );

        json.set(MESSAGES, messages);
        json.set(RESPONSE_FORMAT, responseFormatTieredContracts());
        return json;
    }

    /**
     * AP20: Builds a sequence request with generation constraints injected into the prompt (3-tier).
     *
     * @param taskDescription    the teacher's sample task description, or null
     * @param concepts           the concepts the sequence should cover
     * @param codeSnippet        the teacher's code snippet, or null
     * @param language           the programming language of the tasks
     * @param targetLanguage     the human language of the problem statements
     * @param distractorsEnabled whether to plan distractors
     * @param constraints        the teacher's generation constraints, or null
     * @return the chat completions request
     */
    public ObjectNode buildSequenceRequest(
        final String taskDescription, final List<String> concepts,
        final String codeSnippet, final String language,
        final String targetLanguage, final boolean distractorsEnabled,
        final GenerationConstraints constraints
    ) {
        return buildSequenceRequest(
            taskDescription, concepts, codeSnippet, language,
            targetLanguage, distractorsEnabled, constraints, 3
        );
    }

    /**
     * AP21: Full overload — tier-count-aware sequence request with constraints + distractors. This is the terminal
     * overload that all other overloads ultimately delegate to for 5-tier.
     *
     * @param taskDescription    the teacher's sample task description, or null
     * @param concepts           the concepts the sequence should cover
     * @param codeSnippet        the teacher's code snippet, or null
     * @param language           the programming language of the tasks
     * @param targetLanguage     the human language of the problem statements
     * @param distractorsEnabled whether to plan distractors
     * @param constraints        the teacher's generation constraints, or null
     * @param tierCount          the number of tiers
     * @return the chat completions request
     */
    @SuppressWarnings("checkstyle:MethodLength")
    public ObjectNode buildSequenceRequest(
        final String taskDescription, final List<String> concepts,
        final String codeSnippet, final String language,
        final String targetLanguage, final boolean distractorsEnabled,
        final GenerationConstraints constraints, final int tierCount
    ) {
        if (tierCount <= 3) {
            // Standard 3-tier path — delegate to existing distractor-aware overload + patch constraints
            ObjectNode json = buildSequenceRequest(
                taskDescription, concepts, codeSnippet, language,
                targetLanguage, distractorsEnabled
            );
            String constraintSection = buildConstraintSection(constraints, 3);
            if (!constraintSection.isEmpty()) {
                var messages = (ArrayNode) json.get(MESSAGES);
                var userMsg = (ObjectNode) messages.get(messages.size() - 1);
                userMsg.put(CONTENT, userMsg.get(CONTENT).asText() + constraintSection);
            }
            return json;
        }

        // ── 5-Tier path: dedicated prompt + schema ──
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        // System: dedicated 5-tier identity prompt
        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_SYSTEM)
                .put(CONTENT, sequenceIdentity("Sequence5Tier", tierCount))
        );

        String langLabel = ENGLISH.equalsIgnoreCase(targetLanguage) ? "English" : "German";

        String targetLanguageInstruction = """

            IMPORTANT — Human Language Requirement:
            All student-facing text (statement, contract.taskStatement, contract.purposeBehavior,
            contract.prePostConditions) MUST be written in %s.
            Code, function signatures, variable names, and concept names remain in English.
            """.formatted(langLabel);

        String distractorInstruction = distractorsEnabled ? distractorPlanningInstruction(tierCount) : "";

        String prompt = """
            Generate exactly %d tiered programming tasks (tiers 1–%d) for the inputs below.
            Follow the 5-tier progression model from your instructions:
              Tier 1 = Recognition, Tier 2 = Single Construct, Tier 3 = Combination,
              Tier 4 = Nested Complexity, Tier 5 = Synthesis.
            Each tier must be qualitatively different from the previous — not just longer.
            Statements must be student-facing (one sentence).

            Inputs:
            - Task Description: %s
            - Programming Language: %s
            - Programming Concepts: %s
            - Reference Code Snippet:
            %s

            Output (STRICT):
            Return ONLY JSON matching the provided response schema.
            For each task:
            - Provide: tier, statement, and contract.
            - contract must include ALL fields: language, taskStatement, functionSignature,
              purposeBehavior, prePostConditions, examples[1..2] with {input,output}.
            - statement MUST equal contract.taskStatement exactly.
            Examples must be concrete minimal invocation/result pairs appropriate to the functionSignature.
            No placeholders, no empty strings for any field. If a field cannot be filled logically put 'none'.

            Use the reference snippet only as conceptual context, not as a structural template.
            The generated tasks' intended solutions must consist of a single function only, not full programs.
            %s%s""".formatted(
            tierCount, tierCount,
            taskDescription, language, String.join(LIST_SEPARATOR, concepts), codeSnippet,
            targetLanguageInstruction, distractorInstruction
        );

        // Append constraint section if any
        String constraintSection = buildConstraintSection(constraints, tierCount);
        String finalPrompt = constraintSection.isEmpty() ? prompt : prompt + constraintSection;

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_USER)
                .put(CONTENT, finalPrompt)
        );

        json.set(MESSAGES, messages);
        json.set(
            RESPONSE_FORMAT, distractorsEnabled
                ? responseFormatTieredContractsWithDistractors(tierCount)
                : responseFormatTieredContracts(tierCount)
        );
        return json;
    }

    /**
     * AP20: Builds the constraint section to inject into generation prompts.
     */
    String buildConstraintSection(final GenerationConstraints constraints) {
        return buildConstraintSection(constraints, 3);
    }

    /**
     * AP20+AP21: Builds the constraint section, tier-count-aware for correct difficulty slope text.
     */
    @SuppressWarnings("checkstyle:NPathComplexity")
    String buildConstraintSection(final GenerationConstraints constraints, final int tierCount) {
        if (constraints == null || !constraints.hasAny()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n    GENERATION CONSTRAINTS (AP20):\n");

        if (constraints.getBlacklist() != null && !constraints.getBlacklist().isEmpty()) {
            sb.append("    BLACKLIST — The generated code MUST NOT use any of these constructs:\n");
            sb.append("    ").append(
                constraints.getBlacklist().stream()
                    .map(c -> "\"" + c + "\"")
                    .collect(Collectors.joining(LIST_SEPARATOR))
            );
            sb.append("\n    If any blacklisted construct appears in the solution, the task is INVALID.\n");
        }

        if (constraints.getWhitelist() != null && !constraints.getWhitelist().isEmpty()) {
            sb.append("    WHITELIST — The generated code may ONLY use these constructs:\n");
            sb.append("    ").append(
                constraints.getWhitelist().stream()
                    .map(c -> "\"" + c + "\"")
                    .collect(Collectors.joining(LIST_SEPARATOR))
            );
            sb.append("\n    No other programming constructs beyond basic syntax are allowed.\n");
        }

        if (constraints.getDifficultySlope() != null && !constraints.getDifficultySlope().isBlank()) {
            sb.append("    DIFFICULTY SLOPE: ").append(constraints.getDifficultySlope()).append("\n");
            if (tierCount == 5) {
                switch (constraints.getDifficultySlope().toLowerCase(Locale.ROOT)) {
                    case "gentle" -> sb.append(
                        "    → Tiers 1–5 should progress very gently. Even Tier 5 should be approachable for students.\n"
                    );
                    case "steep" ->
                        sb.append("    → Tiers 1–2 trivial, Tier 3 moderate, Tiers 4–5 significantly challenging.\n");
                    default -> sb.append("    → Standard moderate difficulty progression across all 5 tiers.\n");
                }
            }
            else {
                switch (constraints.getDifficultySlope().toLowerCase(Locale.ROOT)) {
                    case "gentle" -> sb.append(
                        "    → Tiers 1-3 should have very gradual complexity increase. Tier 3 should still be approachable.\n"
                    );
                    case "steep" ->
                        sb.append("    → Tier 1 should be trivial, Tier 3 should be significantly challenging.\n");
                    default -> sb.append("    → Standard moderate difficulty progression across tiers.\n");
                }
            }
        }

        if (constraints.getAdditionalContext() != null && !constraints.getAdditionalContext().isBlank()) {
            sb.append("    TEACHER INSTRUCTIONS:\n    ").append(constraints.getAdditionalContext()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Builds the request for a tiered task sequence, optionally asking for a distractor plan per task.
     *
     * @param taskDescription    the teacher's sample task description, or null
     * @param concepts           the concepts the sequence should cover
     * @param codeSnippet        the teacher's code snippet, or null
     * @param language           the programming language of the tasks
     * @param targetLanguage     the human language of the problem statements
     * @param distractorsEnabled whether to plan distractors
     * @return the chat completions request
     */
    @SuppressWarnings("checkstyle:MethodLength")
    public ObjectNode buildSequenceRequest(
        final String taskDescription, final List<String> concepts,
        final String codeSnippet, final String language,
        final String targetLanguage, final boolean distractorsEnabled
    ) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_SYSTEM)
                .put(CONTENT, sequenceIdentity("Sequence", 3))
        );

        String langLabel;
        if (ENGLISH.equalsIgnoreCase(targetLanguage)) {
            langLabel = "English";
        }
        else {
            langLabel = "German";
        }

        String targetLanguageInstruction = """

            IMPORTANT — Human Language Requirement:
            All student-facing text (statement, contract.taskStatement, contract.purposeBehavior,
            contract.prePostConditions) MUST be written in %s.
            Code, function signatures, variable names, and concept names remain in English.
            """.formatted(langLabel);

        String distractorInstruction = distractorsEnabled ? distractorPlanningInstruction(3) : "";

        String prompt = """
            Generate exactly 3 tiered programming tasks (tiers 1–3) for the inputs below.
            Increase complexity across tiers. Statements must be student-facing (one sentence).

            Inputs:
            - Task Description: %s
            - Programming Language: %s
            - Programming Concepts: %s
            - Reference Code Snippet:
            %s

            Output (STRICT):
            Return ONLY JSON matching the provided response schema.
            For each task:
            - Provide: tier, statement, and contract.
            - contract must include ALL fields: language, taskStatement, functionSignature,
              purposeBehavior, prePostConditions, examples[1..2] with {input,output}.
            - statement MUST equal contract.taskStatement exactly.
            Examples must be concrete minimal invocation/result pairs appropriate to the functionSignature.
            No placeholders, no empty strings for any field. If a field cannot be filled logically put 'none'.

            Use the reference snippet only as conceptual context, not as a structural template.
            The generated tasks' intended solutions must consist of a single function only, not full programs.
            %s%s""".formatted(
            taskDescription, language, String.join(LIST_SEPARATOR, concepts), codeSnippet,
            targetLanguageInstruction, distractorInstruction
        );

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_USER)
                .put(CONTENT, prompt)
        );

        json.set(MESSAGES, messages);
        json.set(
            RESPONSE_FORMAT,
            distractorsEnabled ? responseFormatTieredContractsWithDistractors() : responseFormatTieredContracts()
        );
        return json;
    }

    // AP21: Tier-count-aware overloads
    private ObjectNode responseFormatTieredContracts(int tierCount) {
        if (tierCount <= 3) {
            return responseFormatTieredContracts();
        }
        return buildTieredContractSchema(tierCount, false);
    }

    private ObjectNode responseFormatTieredContractsWithDistractors(int tierCount) {
        if (tierCount <= 3) {
            return responseFormatTieredContractsWithDistractors();
        }
        return buildTieredContractSchema(tierCount, true);
    }

    /**
     * AP21: Builds a dynamic JSON response schema for N tiers, with optional distractor plan.
     */
    @SuppressWarnings("checkstyle:MethodLength")
    private ObjectNode buildTieredContractSchema(int tierCount, boolean withDistractors) {
        ObjectNode rf = mapper.createObjectNode().put(TYPE, JSON_SCHEMA);
        ObjectNode js = mapper.createObjectNode();
        js.put(
            NAME, withDistractors
                ? "tiered_contracts_with_distractors_response"
                : "tiered_contracts_response"
        );
        js.put(STRICT, true);

        ObjectNode schema = mapper.createObjectNode().put(TYPE, OBJECT);
        ObjectNode props = mapper.createObjectNode();

        ObjectNode tasks = mapper.createObjectNode();
        tasks.put(TYPE, ARRAY);
        tasks.put(MIN_ITEMS, tierCount);
        tasks.put(MAX_ITEMS, tierCount);

        ObjectNode taskItem = mapper.createObjectNode().put(TYPE, OBJECT);
        ObjectNode taskProps = mapper.createObjectNode();

        ObjectNode tier = mapper.createObjectNode();
        tier.put(TYPE, "integer");
        tier.put("minimum", 1);
        tier.put("maximum", tierCount);
        taskProps.set(TIER, tier);
        taskProps.set(STATEMENT, str());

        // contract
        ObjectNode contract = mapper.createObjectNode().put(TYPE, OBJECT);
        ObjectNode cProps = mapper.createObjectNode();
        cProps.set(LANGUAGE, str());
        cProps.set(TASK_STATEMENT, str());
        cProps.set(FUNCTION_SIGNATURE, str());
        cProps.set(PURPOSE_BEHAVIOR, str());
        cProps.set(PRE_POST_CONDITIONS, str());
        ObjectNode examples = mapper.createObjectNode();
        examples.put(TYPE, ARRAY);
        examples.put(MIN_ITEMS, 1);
        examples.put(MAX_ITEMS, 2);
        ObjectNode exItem = mapper.createObjectNode().put(TYPE, OBJECT);
        ObjectNode exProps = mapper.createObjectNode();
        exProps.set(INPUT, str());
        exProps.set(OUTPUT, str());
        exItem.set(PROPERTIES, exProps);
        exItem.putArray(REQUIRED).add(INPUT).add(OUTPUT);
        exItem.put(ADDITIONAL_PROPERTIES, false);
        examples.set(ITEMS, exItem);
        cProps.set(EXAMPLES, examples);
        contract.set(PROPERTIES, cProps);
        contract.putArray(REQUIRED)
            .add(LANGUAGE).add(TASK_STATEMENT).add(FUNCTION_SIGNATURE)
            .add(PURPOSE_BEHAVIOR).add(PRE_POST_CONDITIONS).add(EXAMPLES);
        contract.put(ADDITIONAL_PROPERTIES, false);
        taskProps.set(CONTRACT, contract);

        ArrayNode required = taskItem.putArray(REQUIRED);
        required.add(TIER).add(STATEMENT).add(CONTRACT);

        if (withDistractors) {
            ObjectNode distractorPlan = mapper.createObjectNode().put(TYPE, OBJECT);
            ObjectNode dpProps = mapper.createObjectNode();
            dpProps.set(TYPE, str());
            dpProps.set(RATIONALE, str());
            distractorPlan.set(PROPERTIES, dpProps);
            distractorPlan.putArray(REQUIRED).add(TYPE).add(RATIONALE);
            distractorPlan.put(ADDITIONAL_PROPERTIES, false);
            taskProps.set("distractorPlan", distractorPlan);
            required.add("distractorPlan");
        }

        taskItem.set(PROPERTIES, taskProps);
        taskItem.put(ADDITIONAL_PROPERTIES, false);
        tasks.set(ITEMS, taskItem);
        props.set(TASKS, tasks);

        schema.set(PROPERTIES, props);
        schema.putArray(REQUIRED).add(TASKS);
        schema.put(ADDITIONAL_PROPERTIES, false);
        js.set(SCHEMA, schema);
        rf.set(JSON_SCHEMA, js);
        return rf;
    }

    @SuppressWarnings("checkstyle:MethodLength")
    private ObjectNode responseFormatTieredContracts() {
        ObjectNode rf = mapper.createObjectNode().put(TYPE, JSON_SCHEMA);
        ObjectNode js = mapper.createObjectNode();
        js.put(NAME, "tiered_contracts_response");
        js.put(STRICT, true);

        ObjectNode schema = mapper.createObjectNode().put(TYPE, OBJECT);
        ObjectNode props = mapper.createObjectNode();

        // tasks: array[3] of task objects
        ObjectNode tasks = mapper.createObjectNode();
        tasks.put(TYPE, ARRAY);
        tasks.put(MIN_ITEMS, 3);
        tasks.put(MAX_ITEMS, 3);

        // task item
        ObjectNode taskItem = mapper.createObjectNode().put(TYPE, OBJECT);
        ObjectNode taskProps = mapper.createObjectNode();

        // tier
        ObjectNode tier = mapper.createObjectNode();
        tier.put(TYPE, "integer");
        tier.put("minimum", 1);
        tier.put("maximum", 3);
        taskProps.set(TIER, tier);

        // statement (should equal contract.taskStatement)
        ObjectNode statement = mapper.createObjectNode().put(TYPE, STRING);
        taskProps.set(STATEMENT, statement);

        // contract
        ObjectNode contract = mapper.createObjectNode().put(TYPE, OBJECT);
        ObjectNode cProps = mapper.createObjectNode();
        cProps.set(LANGUAGE, str());
        cProps.set(TASK_STATEMENT, str());
        cProps.set(FUNCTION_SIGNATURE, str());
        cProps.set(PURPOSE_BEHAVIOR, str());
        cProps.set(PRE_POST_CONDITIONS, str());

        // examples: array of 1..2 {input, output}
        ObjectNode examples = mapper.createObjectNode();
        examples.put(TYPE, ARRAY);
        examples.put(MIN_ITEMS, 1);
        examples.put(MAX_ITEMS, 2);

        ObjectNode exItem = mapper.createObjectNode().put(TYPE, OBJECT);
        ObjectNode exProps = mapper.createObjectNode();
        exProps.set(INPUT, str());
        exProps.set(OUTPUT, str());
        exItem.set(PROPERTIES, exProps);
        exItem.putArray(REQUIRED).add(INPUT).add(OUTPUT);
        exItem.put(ADDITIONAL_PROPERTIES, false);

        examples.set(ITEMS, exItem);
        cProps.set(EXAMPLES, examples);

        contract.set(PROPERTIES, cProps);
        contract.putArray(REQUIRED)
            .add(LANGUAGE)
            .add(TASK_STATEMENT)
            .add(FUNCTION_SIGNATURE)
            .add(PURPOSE_BEHAVIOR)
            .add(PRE_POST_CONDITIONS)
            .add(EXAMPLES);
        contract.put(ADDITIONAL_PROPERTIES, false);

        taskProps.set(CONTRACT, contract);

        taskItem.set(PROPERTIES, taskProps);
        taskItem.putArray(REQUIRED).add(TIER).add(STATEMENT).add(CONTRACT);
        taskItem.put(ADDITIONAL_PROPERTIES, false);

        tasks.set(ITEMS, taskItem);
        props.set(TASKS, tasks);

        schema.set(PROPERTIES, props);
        schema.putArray(REQUIRED).add(TASKS);
        schema.put(ADDITIONAL_PROPERTIES, false);

        js.set(SCHEMA, schema);
        rf.set(JSON_SCHEMA, js);
        return rf;
    }

    @SuppressWarnings("checkstyle:MethodLength")
    private ObjectNode responseFormatTieredContractsWithDistractors() {
        ObjectNode rf = mapper.createObjectNode().put(TYPE, JSON_SCHEMA);
        ObjectNode js = mapper.createObjectNode();
        js.put(NAME, "tiered_contracts_with_distractors_response");
        js.put(STRICT, true);

        ObjectNode schema = mapper.createObjectNode().put(TYPE, OBJECT);
        ObjectNode props = mapper.createObjectNode();

        ObjectNode tasks = mapper.createObjectNode();
        tasks.put(TYPE, ARRAY);
        tasks.put(MIN_ITEMS, 3);
        tasks.put(MAX_ITEMS, 3);

        ObjectNode taskItem = mapper.createObjectNode().put(TYPE, OBJECT);
        ObjectNode taskProps = mapper.createObjectNode();

        ObjectNode tier = mapper.createObjectNode();
        tier.put(TYPE, "integer");
        tier.put("minimum", 1);
        tier.put("maximum", 3);
        taskProps.set(TIER, tier);

        taskProps.set(STATEMENT, str());

        // contract (same as original)
        ObjectNode contract = mapper.createObjectNode().put(TYPE, OBJECT);
        ObjectNode cProps = mapper.createObjectNode();
        cProps.set(LANGUAGE, str());
        cProps.set(TASK_STATEMENT, str());
        cProps.set(FUNCTION_SIGNATURE, str());
        cProps.set(PURPOSE_BEHAVIOR, str());
        cProps.set(PRE_POST_CONDITIONS, str());

        ObjectNode examples = mapper.createObjectNode();
        examples.put(TYPE, ARRAY);
        examples.put(MIN_ITEMS, 1);
        examples.put(MAX_ITEMS, 2);

        ObjectNode exItem = mapper.createObjectNode().put(TYPE, OBJECT);
        ObjectNode exProps = mapper.createObjectNode();
        exProps.set(INPUT, str());
        exProps.set(OUTPUT, str());
        exItem.set(PROPERTIES, exProps);
        exItem.putArray(REQUIRED).add(INPUT).add(OUTPUT);
        exItem.put(ADDITIONAL_PROPERTIES, false);

        examples.set(ITEMS, exItem);
        cProps.set(EXAMPLES, examples);

        contract.set(PROPERTIES, cProps);
        contract.putArray(REQUIRED)
            .add(LANGUAGE)
            .add(TASK_STATEMENT)
            .add(FUNCTION_SIGNATURE)
            .add(PURPOSE_BEHAVIOR)
            .add(PRE_POST_CONDITIONS)
            .add(EXAMPLES);
        contract.put(ADDITIONAL_PROPERTIES, false);

        taskProps.set(CONTRACT, contract);

        // distractorPlan
        ObjectNode distractorPlan = mapper.createObjectNode().put(TYPE, OBJECT);
        ObjectNode dpProps = mapper.createObjectNode();
        dpProps.set(TYPE, str());
        dpProps.set(RATIONALE, str());
        distractorPlan.set(PROPERTIES, dpProps);
        distractorPlan.putArray(REQUIRED).add(TYPE).add(RATIONALE);
        distractorPlan.put(ADDITIONAL_PROPERTIES, false);
        taskProps.set("distractorPlan", distractorPlan);

        taskItem.set(PROPERTIES, taskProps);
        taskItem.putArray(REQUIRED).add(TIER).add(STATEMENT).add(CONTRACT).add("distractorPlan");
        taskItem.put(ADDITIONAL_PROPERTIES, false);

        tasks.set(ITEMS, taskItem);
        props.set(TASKS, tasks);

        schema.set(PROPERTIES, props);
        schema.putArray(REQUIRED).add(TASKS);
        schema.put(ADDITIONAL_PROPERTIES, false);

        js.set(SCHEMA, schema);
        rf.set(JSON_SCHEMA, js);
        return rf;
    }

    private ObjectNode str() {
        return mapper.createObjectNode().put(TYPE, STRING);
    }

    /**
     * Constructs a GPT request to solve a programming task and return only code.
     *
     * @param contract SolveContract composing the input for the request
     * @return structured GPT request JSON
     */
    public ObjectNode buildSolveRequest(final SolveContract contract) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        // SYSTEM: jetzt klar auf JSON { "code": "..." } festnageln
        final String systemPrompt = "You are CodeOwl (Solver).\n"
            + "Return only valid JSON matching the response schema: a single string field \"code\".\n"
            + "No markdown/backticks, no extra keys, no text outside JSON.\n\n"
            + "Rules:\n"
            + "- Put the entire solution as the string value of \"code\" (escape as needed).\n"
            + "- Single file; use only the standard library.\n"
            + "- No file/network I/O unless shown in the examples.\n"
            + "- Obey the specified language and any provided function signature.\n"
            + "- Implement exactly what the spec and examples require; avoid adding features.\n"
            + "- Prefer clear, minimal, idiomatic code.";

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_SYSTEM)
                .put(CONTENT, systemPrompt)
        );

        // USER: CONTRACT wie gehabt
        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_USER)
                .put(CONTENT, composeContract(contract))
        );

        json.set(MESSAGES, messages);

        // WICHTIG: wieder JSON-Schema für {"code": "..."} erzwingen
        json.set(RESPONSE_FORMAT, responseFormatSolverResponse());  // <— statt "text"

        return json;
    }

    private static String composeContract(SolveContract c) {
        StringBuilder sb = new StringBuilder();
        sb.append("CONTRACT\n");
        sb.append("Language: ").append(c.language()).append('\n');

        sb.append("Task statement (student-facing, one sentence): ")
            .append(c.taskStatement()).append('\n');

        sb.append("Function signature: ").append(c.functionSignature()).append('\n');

        sb.append("Spec:\n\"\"\"\n")
            .append(sanitizeTripleQuotes(c.purposeBehavior())).append('\n')
            .append(sanitizeTripleQuotes(c.prePostConditions())).append('\n')
            .append("\"\"\"\n");

        sb.append("Examples:\nInput -> Output:\n");
        for (SolveContract.ExamplePair ex : c.examples()) {
            sb.append(ex.input()).append(" -> ").append(ex.output()).append('\n');
        }

        sb.append("\nGOAL\n")
            .append("Write the final solution that matches the spec and examples exactly.");
        return sb.toString();
    }

    private static String sanitizeTripleQuotes(String s) {
        if (s == null) {
            return "";
        }
        // Avoid accidental closing of the docstring fence
        return s.replace("\"\"\"", "\\\"\\\"\\\"");
    }

    /**
     * Constructs a GPT request for extracting programming concepts from a code snippet using the fixed concept catalog
     * (AP25).
     *
     * @param codeSnippet   code to analyze
     * @param language      programming language (e.g. "java", "python", "javascript")
     * @param catalogPrompt pre-formatted catalog string (slug — name: description) filtered for language
     * @return GPT request JSON with strict response schema
     */
    public ObjectNode buildConceptExtractionRequest(
        final String codeSnippet,
        final String language,
        final String catalogPrompt
    ) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_SYSTEM)
                .put(CONTENT, identityLoader.load("ConceptExtraction"))
        );

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_USER)
                .put(
                    CONTENT,
                    """
                        Programming language: %s

                        Available concept catalog (only use slugs from this list, filtered for %s):
                        %s

                        ---

                        Code snippet:
                        ```%s
                        %s
                        ```

                        ---

                        Task:
                        Identify the programming concepts that are directly and explicitly present in the code snippet above.

                        Requirements:
                        - Return between 1 and 5 concept slugs.
                        - Use ONLY slugs from the catalog above.
                        - Only include concepts actually visible in the code — not assumed or implied.
                        - Do not return concepts specific to a different programming language.
                        - Output valid JSON using exactly this schema: {"concepts": ["slug1", "slug2"]}"""
                        .formatted(language, language, catalogPrompt, language, codeSnippet)
                )
        );

        json.set(MESSAGES, messages);
        json.set(RESPONSE_FORMAT, responseFormatConceptList());
        return json;
    }

    /**
     * Builds a chat.completions request using an explicit system prompt and user prompt body. If the user prompt
     * contains the token "{{CODE}}", it will be replaced with the given codeSnippet. If "{{CODE}}" is absent, the code
     * will be appended as a "Code:" block at the end. The response is constrained to a JSON object with a single "task"
     * field via response_format.
     *
     * @param codeSnippet    the code to describe
     * @param systemPrompt   the system prompt to send
     * @param userPromptBody the user prompt to send with the code
     * @return the chat completions request
     */
    public ObjectNode buildTaskDescriptionRequest(
        final String codeSnippet,
        final String systemPrompt,
        final String userPromptBody
    ) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        // System message: use provided systemPrompt if non-empty; otherwise keep it empty (or you could fall back to an
        // identity if you want)
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(
                mapper.createObjectNode()
                    .put(ROLE, ROLE_SYSTEM)
                    .put(CONTENT, systemPrompt)
            );
        }

        // Resolve {{CODE}} placeholder if present; else append code block
        String resolvedBody;
        if (userPromptBody != null && userPromptBody.contains("{{CODE}}")) {
            resolvedBody = userPromptBody.replace("{{CODE}}", codeSnippet);
        }
        else {
            String safeBody = userPromptBody == null ? "" : userPromptBody;
            resolvedBody = safeBody + "\n\nCode:\n" + codeSnippet;
        }

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_USER)
                .put(CONTENT, resolvedBody)
        );

        json.set(MESSAGES, messages);
        json.set(RESPONSE_FORMAT, responseFormatTask()); // enforce {"task": "..."}
        return json;
    }

    /**
     * Constructs a GPT request to generate a task description from a code snippet.
     *
     * @param codeSnippet the code to summarize
     * @return GPT request JSON with strict task field response
     */
    public ObjectNode buildTaskDescriptionRequest(final String codeSnippet) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_SYSTEM)
                .put(CONTENT, identityLoader.load("TaskDescription"))
        );

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_USER)
                .put(
                    CONTENT, "Given the following code, generate"
                        + " a concise and clear problem statement for it. "
                        + "Respond in JSON format only.\nCode:\n"
                        + codeSnippet
                )
        );

        json.set(MESSAGES, messages);
        json.set(RESPONSE_FORMAT, responseFormatTask());
        return json;
    }

    /**
     * Constructs a GPT request to generate a task from a list of concepts in a given language.
     *
     * @param concepts list of programming concepts
     * @param language target programming language
     * @return GPT request JSON with strict task field
     */
    public ObjectNode buildTaskDescriptionRequest(
        final List<String> concepts,
        final String language
    ) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_SYSTEM)
                .put(
                    CONTENT, "You are an expert in designing"
                        + " programming exercises for students."
                )
        );

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_USER)
                .put(
                    CONTENT, "Generate a single concise"
                        + " programming task written in " + language
                        + " that emphasizes the following concepts: "
                        + String.join(LIST_SEPARATOR, concepts)
                        + ".\nRespond with only a JSON object like: "
                        + "{\"task\": \"...\"}"
                )
        );

        json.set(MESSAGES, messages);
        json.set(RESPONSE_FORMAT, responseFormatTask());
        return json;
    }

    /**
     * Constructs a GPT request to detect the programming language used in a code snippet.
     *
     * @param codeSnippet code whose language should be identified
     * @return GPT request JSON
     */
    public ObjectNode buildLanguageDetectionRequest(final String codeSnippet) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_SYSTEM)
                .put(CONTENT, identityLoader.load("LanguageDetection"))
        );

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_USER)
                .put(
                    CONTENT, "Detect the programming language"
                        + " used in the following code. Respond only in JSON "
                        + "format.\nCode:\n" + codeSnippet
                )
        );

        json.set(MESSAGES, messages);
        json.set(RESPONSE_FORMAT, responseFormatLanguage());
        return json;
    }

    // === Response Format Helpers ===

    /**
     * Builds the request that solves a problem statement, answered as code only.
     *
     * @param taskDescription the problem statement to solve
     * @param language        the programming language of the solution
     * @return the chat completions request
     */
    public ObjectNode buildSimpleSolveRequest(final String taskDescription, final String language) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        // System: strikt JSON mit "code"
        final String systemPrompt = "You are CodeOwl (Simple Solver).\n"
            + "Return only valid JSON with a single string field \"code\".\n"
            + "No markdown/backticks, no explanations, no extra keys, no text outside JSON.\n\n"
            + "Rules:\n"
            + "- Put the entire solution as the string value of \"code\" (escape as needed).\n"
            + "- Single file; use only the standard library.\n"
            + "- No file/network I/O unless the task explicitly requires it.\n"
            + "- Obey the specified language.\n"
            + "- Prefer clear, minimal, idiomatic code.";

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_SYSTEM)
                .put(CONTENT, systemPrompt)
        );

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_USER)
                .put(CONTENT, composeSimpleSolveContract(taskDescription, language))
        );

        json.set(MESSAGES, messages);
        json.set(RESPONSE_FORMAT, responseFormatSolverResponse()); // erzwinge {"code": "..."}
        return json;
    }

    /**
     * Loads a sequence identity and fills in its distractor placeholders from {@link DistractorStrategy}, so the
     * taxonomy and tier mapping the sequence prompt plans with are the ones every other distractor path uses.
     *
     * @param name      the identity file, {@code Sequence} or {@code Sequence5Tier}
     * @param tierCount how many tiers the sequence will have
     * @return the identity text, with no placeholder left
     */
    String sequenceIdentity(final String name, final int tierCount) {
        return identityLoader.load(name)
            .replace(DISTRACTOR_TAXONOMY_PLACEHOLDER, DistractorStrategy.taxonomyText())
            .replace(DISTRACTOR_TIER_MAPPING_PLACEHOLDER, DistractorStrategy.tierMappingText(tierCount));
    }

    /**
     * The user-prompt instruction asking the sequence prompt for a {@code distractorPlan} per task.
     *
     * @param tierCount how many tiers the sequence will have
     * @return the instruction, starting with a blank line
     */
    private static String distractorPlanningInstruction(final int tierCount) {
        return """

            DISTRACTOR PLANNING:
            For each task, include a "distractorPlan" object with:
            - "type": one of %s
            - "rationale": a short sentence explaining why this error type fits the task and tier
            Match distractor difficulty to tier level:
            %s
            """.formatted(DistractorStrategy.typeNames(), DistractorStrategy.tierMappingText(tierCount));
    }

    /**
     * Constructs a GPT request to generate distractor lines for a Parsons problem (AP15). The "Antagonist" prompt takes
     * a correct solution and a distractor plan, then generates plausible but incorrect code lines.
     *
     * <p>
     * Without a plan the model picks one itself, the way the sequence prompt does on the automatic creator: one type
     * from those {@link DistractorStrategy#preferredTypes} names for the tier, with a rationale. The response then
     * carries that choice as {@code plan} next to the distractors.
     *
     * @param solutionCode   the correct solution code
     * @param language       programming language
     * @param taskStatement  the task description
     * @param tierLevel      difficulty tier
     * @param tierCount      how many tiers the task's sequence has
     * @param distractorPlan JSON string with type and rationale, or null
     * @param count          number of distractors to generate
     * @return GPT-compliant JSON request object
     */
    @SuppressWarnings("checkstyle:MethodLength")
    public ObjectNode buildDistractorRequest(
        final String solutionCode,
        final String language,
        final String taskStatement,
        final int tierLevel,
        final int tierCount,
        final String distractorPlan,
        final int count
    ) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        String systemPrompt = """
            You are CodeOwl (Distractor Expert). Your goal is to generate plausible but \
            incorrect code lines (distractors) for Parsons problems.

            A Parsons problem requires students to arrange code blocks. Distractors are \
            "trap" lines that look correct but introduce logical, boundary, or syntax errors.

            RULES:
            1. Generate distractors highly similar in style and variable naming to the solution.
            2. Each distractor must be a SINGLE line or a small coherent block (max 2 lines).
            3. Every distractor MUST end with the suffix " #distractor".
            4. Do NOT use #swap or #group markers on distractor lines.
            5. Ensure distractors are functionally incorrect for the given task.
            6. The distractors must be valid syntax for the given programming language.
            7. Focus on common student misconceptions matching the requested taxonomy type.
            8. Match the indentation level as if the distractor replaced an existing line.

            TAXONOMY TYPES:
            %s""".formatted(DistractorStrategy.taxonomyText());

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_SYSTEM)
                .put(CONTENT, systemPrompt)
        );

        boolean hasPlan = distractorPlan != null && !distractorPlan.isBlank();
        List<DistractorStrategy.Type> preferred = DistractorStrategy.preferredTypes(tierLevel, tierCount);
        String strategySection;
        if (hasPlan) {
            strategySection = "\nDISTRACTOR STRATEGY:\n" + distractorPlan + "\n";
        }
        else {
            strategySection = """

                DISTRACTOR STRATEGY SELECTION:
                This is tier %d of %d. Choose ONE type from the types preferred for this tier:
                %s
                Pick the one that fits this code best, give a one-sentence rationale for why it fits the task \
                and tier, and generate all distractors of that type.
                """.formatted(
                tierLevel, tierCount, DistractorStrategy.taxonomyText(preferred)
            );
        }

        String userPrompt = """
            TASK CONTEXT:
            Language: %s
            Task: %s
            Tier: %d
            Correct Solution:
            \"\"\"
            %s
            \"\"\"
            %s
            GOAL:
            Generate %d distractors for this task.

            OUTPUT:
            Return ONLY a JSON object with a "distractors" array containing the distractor strings%s.
            Each string must end with " #distractor".""".formatted(
            language, taskStatement, tierLevel, solutionCode, strategySection, count,
            hasPlan ? "" : " and a \"plan\" object with the chosen \"type\" and your \"rationale\""
        );

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_USER)
                .put(CONTENT, userPrompt)
        );

        json.set(MESSAGES, messages);
        json.set(RESPONSE_FORMAT, responseFormatDistractors(hasPlan ? null : preferred));
        return json;
    }

    /**
     * AP19 Teil A: Builds the semantic tagging request. The response is held to the tagger's own schema, so it can only
     * come back as the tag object — not as free text.
     *
     * @param systemPrompt the tagger's system prompt
     * @param userPrompt   the sequence content to tag
     * @return the chat completions request
     */
    public ObjectNode buildSemanticTagRequest(final String systemPrompt, final String userPrompt) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();
        messages.add(mapper.createObjectNode().put(ROLE, ROLE_SYSTEM).put(CONTENT, systemPrompt));
        messages.add(mapper.createObjectNode().put(ROLE, ROLE_USER).put(CONTENT, userPrompt));
        json.set(MESSAGES, messages);
        json.set(RESPONSE_FORMAT, responseFormatSemanticTags());
        return json;
    }

    private ObjectNode responseFormatSemanticTags() {
        ObjectNode tag = mapper.createObjectNode().put(TYPE, OBJECT);
        ObjectNode tagProps = mapper.createObjectNode();
        tagProps.set("de", mapper.createObjectNode().put(TYPE, STRING));
        tagProps.set(ENGLISH, mapper.createObjectNode().put(TYPE, STRING));
        tag.set(PROPERTIES, tagProps);
        tag.putArray(REQUIRED).add("de").add(ENGLISH);
        tag.put(ADDITIONAL_PROPERTIES, false);

        ObjectNode properties = mapper.createObjectNode();
        properties.set("primary_tags", mapper.createObjectNode().put(TYPE, ARRAY).set(ITEMS, tag));
        properties.set("aliases", mapper.createObjectNode().put(TYPE, ARRAY).set(ITEMS, tag.deepCopy()));
        properties.set("short_description", mapper.createObjectNode().put(TYPE, STRING));

        ObjectNode schema = mapper.createObjectNode().put(TYPE, OBJECT);
        schema.set(PROPERTIES, properties);
        schema.putArray(REQUIRED).add("primary_tags").add("aliases").add("short_description");
        schema.put(ADDITIONAL_PROPERTIES, false);

        ObjectNode js = mapper.createObjectNode();
        js.put(NAME, "semantic_tags_response");
        js.put(STRICT, true);
        js.set(SCHEMA, schema);

        ObjectNode rf = mapper.createObjectNode().put(TYPE, JSON_SCHEMA);
        rf.set(JSON_SCHEMA, js);
        return rf;
    }

    /**
     * The distractor response schema.
     *
     * @param planTypes the types the model may choose from when it has to pick a plan itself, or null when a plan was
     *                  given and the response carries only the distractors
     */
    private ObjectNode responseFormatDistractors(List<DistractorStrategy.Type> planTypes) {
        ObjectNode rf = mapper.createObjectNode().put(TYPE, JSON_SCHEMA);
        ObjectNode js = mapper.createObjectNode();
        js.put(NAME, "distractor_response");
        js.put(STRICT, true);

        ObjectNode schema = mapper.createObjectNode().put(TYPE, OBJECT);
        ObjectNode properties = mapper.createObjectNode();

        ObjectNode distractors = mapper.createObjectNode();
        distractors.put(TYPE, ARRAY);
        ObjectNode items = mapper.createObjectNode().put(TYPE, STRING);
        distractors.set(ITEMS, items);
        properties.set("distractors", distractors);

        ArrayNode required = mapper.createArrayNode().add("distractors");
        if (planTypes != null) {
            ObjectNode type = mapper.createObjectNode().put(TYPE, STRING);
            ArrayNode allowed = type.putArray("enum");
            planTypes.forEach(t -> allowed.add(t.name()));

            ObjectNode planProps = mapper.createObjectNode();
            planProps.set(TYPE, type);
            planProps.set(RATIONALE, mapper.createObjectNode().put(TYPE, STRING));

            ObjectNode plan = mapper.createObjectNode().put(TYPE, OBJECT);
            plan.set(PROPERTIES, planProps);
            plan.putArray(REQUIRED).add(TYPE).add(RATIONALE);
            plan.put(ADDITIONAL_PROPERTIES, false);
            properties.set("plan", plan);
            required.add("plan");
        }

        schema.set(PROPERTIES, properties);
        schema.set(REQUIRED, required);
        schema.put(ADDITIONAL_PROPERTIES, false);

        js.set(SCHEMA, schema);
        rf.set(JSON_SCHEMA, js);
        return rf;
    }

    /**
     * AP31: Builds the request for inline-split generation. Given a solved task, asks the model to pick ONE
     * syntactically complex line and decompose it into 2–4 draggable fragments that are EXACT contiguous substrings (so
     * prefix + concat(fragments) + suffix reconstruct the line verbatim), optionally with an OFF_BY_ONE fragment
     * distractor.
     *
     * @param solutionCode  the task's solution
     * @param language      the programming language of the solution
     * @param taskStatement the task's problem statement
     * @param tierLevel     the task's tier
     * @return the chat completions request
     */
    public ObjectNode buildSplitPlanRequest(
        final String solutionCode,
        final String language,
        final String taskStatement,
        final int tierLevel
    ) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        String systemPrompt = """
            You are CodeOwl (Split Expert). For Parsons problems you decompose ONE syntactically \
            complex line (e.g. a for-loop header or a compound condition) into 2–4 inline fragments \
            that students re-order in slots.

            HARD RULES:
            1. Pick at most ONE line — the most instructive complex line. If none is suitable, return {"splits": []}.
            2. lineIndex is the 0-based index of that line in the solution EXACTLY as given below \
               (split on newlines; count every line including braces).
            3. fragments must be 2–4 EXACT, contiguous substrings of the line body. Concatenating \
               prefix + fragments (in order, no separator) + suffix MUST reproduce the line \
               character-for-character, including all spaces. Preserve leading indentation inside prefix.
            4. prefix/suffix are fixed, non-draggable display text (may be empty strings).
            5. Do NOT add any distractors here — split distractors are generated in a separate, \
               split-aware pass. Emit fragments/prefix/suffix only.
            6. Do NOT include gap markers, #distractor, #swap or #group.""";

        messages.add(mapper.createObjectNode().put(ROLE, ROLE_SYSTEM).put(CONTENT, systemPrompt));

        String userPrompt = """
            TASK CONTEXT:
            Language: %s
            Task: %s
            Tier: %d
            Solution (lineIndex = 0-based line number):
            \"\"\"
            %s
            \"\"\"

            OUTPUT:
            Return ONLY a JSON object of this exact shape:
            {"splits":[{"lineIndex":<int>,"fragments":["...","..."],"prefix":"...","suffix":"..."}]}
            Use an empty array {"splits": []} if no line is suitable.""".formatted(
            language, taskStatement, tierLevel, solutionCode
        );

        messages.add(mapper.createObjectNode().put(ROLE, ROLE_USER).put(CONTENT, userPrompt));

        json.set(MESSAGES, messages);
        json.set(RESPONSE_FORMAT, mapper.createObjectNode().put(TYPE, "json_object"));
        return json;
    }

    /**
     * AP31 (second pass): asks for ONE fragment-level distractor that fits a single slot of an already-decided split.
     * The model sees the exact fragments, so its wrong fragment matches one slot's shape instead of spanning the whole
     * line.
     *
     * @param fragments     the correct fragments of the split line, in order
     * @param prefix        the fixed text before the fragments
     * @param suffix        the fixed text after the fragments
     * @param language      the programming language of the line
     * @param taskStatement the task's problem statement
     * @param tierLevel     the task's tier
     * @return the chat completions request
     */
    public ObjectNode buildSplitDistractorRequest(
        final List<String> fragments,
        final String prefix, final String suffix,
        final String language, final String taskStatement,
        final int tierLevel
    ) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        String systemPrompt = """
            You are CodeOwl (Split-Distractor Expert). A Parsons line was ALREADY split into ordered \
            fragments (each fragment is one draggable slot). Produce ONE wrong chip a student might \
            plausibly reach for. Choose ONE of two kinds:

            A) FITTING (harder): a drop-in alternative for EXACTLY ONE fragment — same role and similar \
               shape/length, so it competes for that single slot. Set fragmentIndex to its 0-based \
               position. type in {OFF_BY_ONE (< vs <=, bounds), LOGIC_FLIP (&&/||, ==/!=), \
               VARIABLE_MISMATCH (wrong but existing identifier), BOUNDARY_IDENTITY (wrong init/base value)}.

            B) WRONG_SHAPE (easier): a plausible-looking block that spans several fragments or reshapes \
               the line, so it obviously cannot fit the per-slot structure once the slots are visible. \
               Set type = WRONG_SHAPE and fragmentIndex = -1.

            RULES:
            1. Either kind must be genuinely WRONG (changes behaviour), not a cosmetic re-spelling of a correct fragment.
            2. Difficulty guide: lower tiers suit WRONG_SHAPE (easier, obvious); higher tiers suit a \
               FITTING distractor (harder). Guidance only — pick whatever is most instructive here.
            3. If nothing instructive applies, decline by returning {"text": ""}.
            4. Output JSON only — no #distractor/#swap/#group, no markdown.""";

        messages.add(mapper.createObjectNode().put(ROLE, ROLE_SYSTEM).put(CONTENT, systemPrompt));

        StringBuilder fragList = new StringBuilder();
        for (int i = 0; i < fragments.size(); i++) {
            fragList.append("[").append(i).append("] \"").append(fragments.get(i)).append("\"\n");
        }

        String userPrompt = """
            Language: %s
            Task: %s
            Tier: %d
            Fixed prefix (not draggable): "%s"
            Fixed suffix (not draggable): "%s"
            Fragments (0-based; each is one slot):
            %s
            Return ONLY: {"fragmentIndex":<0-based int, or -1 for WRONG_SHAPE>,"text":"<wrong chip>","type":"<TYPE>"}
            Decline with {"text": ""} if nothing instructive applies.""".formatted(
            language, taskStatement, tierLevel,
            prefix == null ? "" : prefix, suffix == null ? "" : suffix, fragList.toString()
        );

        messages.add(mapper.createObjectNode().put(ROLE, ROLE_USER).put(CONTENT, userPrompt));

        json.set(MESSAGES, messages);
        json.set(RESPONSE_FORMAT, mapper.createObjectNode().put(TYPE, "json_object"));
        return json;
    }

    private static String composeSimpleSolveContract(final String task, final String language) {
        StringBuilder sb = new StringBuilder();
        sb.append("CONTRACT (simple)\n");
        sb.append("Language: ").append(language).append('\n');
        sb.append("Task statement (one sentence): ").append(task).append('\n');
        sb.append("\nGOAL\nWrite the final solution that matches the task exactly.");
        return sb.toString();
    }

    private ObjectNode responseFormatSolverResponse() {
        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put(TYPE, JSON_SCHEMA);

        ObjectNode jsonSchema = mapper.createObjectNode();
        jsonSchema.put(NAME, "code_solution_response");
        jsonSchema.put(STRICT, true);

        ObjectNode schema = mapper.createObjectNode();
        schema.put(TYPE, OBJECT);

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode code = mapper.createObjectNode();
        code.put(TYPE, STRING);
        properties.set("code", code);

        schema.set(PROPERTIES, properties);
        schema.putArray(REQUIRED).add("code");
        schema.put(ADDITIONAL_PROPERTIES, false);

        jsonSchema.set(SCHEMA, schema);
        responseFormat.set(JSON_SCHEMA, jsonSchema);
        return responseFormat;
    }

    private ObjectNode responseFormatConceptList() {
        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put(TYPE, JSON_SCHEMA);

        ObjectNode jsonSchema = mapper.createObjectNode();
        jsonSchema.put(NAME, "concept_list_response");
        jsonSchema.put(STRICT, true);

        ObjectNode schema = mapper.createObjectNode();
        schema.put(TYPE, OBJECT);

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode concepts = mapper.createObjectNode();
        concepts.put(TYPE, ARRAY);
        ObjectNode items = mapper.createObjectNode();
        items.put(TYPE, STRING);
        concepts.set(ITEMS, items);

        properties.set("concepts", concepts);
        schema.set(PROPERTIES, properties);
        schema.putArray(REQUIRED).add("concepts");
        schema.put(ADDITIONAL_PROPERTIES, false);

        jsonSchema.set(SCHEMA, schema);
        responseFormat.set(JSON_SCHEMA, jsonSchema);
        return responseFormat;
    }

    private ObjectNode responseFormatTask() {
        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put(TYPE, JSON_SCHEMA);

        ObjectNode jsonSchema = mapper.createObjectNode();
        jsonSchema.put(NAME, "sample_task_response");
        jsonSchema.put(STRICT, true);

        ObjectNode schema = mapper.createObjectNode();
        schema.put(TYPE, OBJECT);

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode task = mapper.createObjectNode();
        task.put(TYPE, STRING);
        properties.set("task", task);

        schema.set(PROPERTIES, properties);
        schema.putArray(REQUIRED).add("task");
        schema.put(ADDITIONAL_PROPERTIES, false);

        jsonSchema.set(SCHEMA, schema);
        responseFormat.set(JSON_SCHEMA, jsonSchema);
        return responseFormat;
    }

    private ObjectNode responseFormatLanguage() {
        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put(TYPE, JSON_SCHEMA);

        ObjectNode jsonSchema = mapper.createObjectNode();
        jsonSchema.put(NAME, "language_detection_response");
        jsonSchema.put(STRICT, true);

        ObjectNode schema = mapper.createObjectNode();
        schema.put(TYPE, OBJECT);

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode language = mapper.createObjectNode();
        language.put(TYPE, STRING);
        // A closed set, not a free string. Structured output enforces this, so a typo like
        // "Pyhton" -- which once reached the database and silently cost six sequences their
        // static analysis -- is no longer a value the model is able to return.
        ArrayNode allowed = language.putArray("enum");
        SupportedLanguage.detectionChoices().forEach(allowed::add);
        properties.set(LANGUAGE, language);

        schema.set(PROPERTIES, properties);
        schema.putArray(REQUIRED).add(LANGUAGE);
        schema.put(ADDITIONAL_PROPERTIES, false);

        jsonSchema.set(SCHEMA, schema);
        responseFormat.set(JSON_SCHEMA, jsonSchema);
        return responseFormat;
    }

    // ════════════════════════════════════════════════════════════
    // AP19: Curriculum-based generation
    // ════════════════════════════════════════════════════════════

    /**
     * Builds a curriculum-based sequence generation request with seed code examples.
     *
     * @param topic              the curriculum topic (with grade, track, learning area)
     * @param examples           curated code examples for this topic
     * @param concepts           programming concepts to exercise
     * @param language           programming language
     * @param targetLanguage     "de" or "en"
     * @param tierCount          3 or 5
     * @param distractorsEnabled whether to include distractor plans
     * @param constraints        optional AP20 constraints
     * @return the chat completions request
     */
    @SuppressWarnings({ "checkstyle:MethodLength", "checkstyle:NPathComplexity" })
    public ObjectNode buildCurriculumSequenceRequest(
        final CurriculumTopicDTO topic,
        final List<CurriculumCodeExampleDTO> examples,
        final List<String> concepts,
        final String language,
        final String targetLanguage,
        final int tierCount,
        final boolean distractorsEnabled,
        final GenerationConstraints constraints
    ) {

        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        // System prompt: dedicated curriculum identity
        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_SYSTEM)
                .put(CONTENT, identityLoader.load("SequenceCurriculum"))
        );

        // Build seed examples block (AP32: few-shot). A topic without curated examples is
        // rejected upstream (CurriculumGenerationService throws), so the list is non-empty here.
        StringBuilder seedBlock = new StringBuilder();
        for (int i = 0; i < examples.size(); i++) {
            var ex = examples.get(i);
            seedBlock.append("--- Example %d: \"%s\" ---\n".formatted(i + 1, ex.getTitle()));
            seedBlock.append("Language: %s\n".formatted(ex.getLanguage()));
            if (ex.getDifficulty() != null && !ex.getDifficulty().isBlank()) {
                seedBlock.append("Difficulty: %s\n".formatted(ex.getDifficulty()));
            }
            seedBlock.append("Code:\n%s\n".formatted(ex.getCode()));
            if (ex.getProblemStatement() != null && !ex.getProblemStatement().isBlank()) {
                seedBlock.append("Description: %s\n".formatted(ex.getProblemStatement()));
            }
            if (ex.getNotes() != null && !ex.getNotes().isBlank()) {
                seedBlock.append("Didactic notes from the teacher: %s\n".formatted(ex.getNotes()));
            }
            seedBlock.append("\n");
        }

        String langLabel = ENGLISH.equalsIgnoreCase(targetLanguage) ? "English" : "German";

        // The curriculum tree holds school Jahrgangsstufen and university
        // modules. Describing a module as "Jahrgangsstufe 9, Track NTG" — which
        // is what the old fixed defaults did for a topic without a grade —
        // tells the model the wrong audience, so each line is only written when
        // the topic actually has that field.
        StringBuilder context = new StringBuilder();
        context.append("Topic: %s\n".formatted(topic.getName()));
        context.append(
            "Educational level: %s\n".formatted(
                "UNIVERSITY".equalsIgnoreCase(topic.getEducationLevel())
                    ? "University course — students are adults in higher education"
                    : "Secondary school (Gymnasium, Bayern LehrplanPLUS)"
            )
        );
        if (topic.getGradeLevel() != null) {
            context.append("Grade: Jahrgangsstufe %d\n".formatted(topic.getGradeLevel()));
        }
        if (topic.getTrack() != null && !topic.getTrack().isBlank()) {
            context.append("Track: %s\n".formatted(topic.getTrack()));
        }
        if (topic.getLearningArea() != null && !topic.getLearningArea().isBlank()) {
            context.append("Learning Area: %s\n".formatted(topic.getLearningArea()));
        }

        String distractorLine = distractorsEnabled
            ? "Include distractor plans."
            : "Do NOT include distractor plans.";

        String prompt = """
            Generate a %d-tier Parsons puzzle task sequence for the following curriculum context.

            ── CURRICULUM CONTEXT ──
            %s
            Programming Language: %s
            Target Concepts: %s
            Task Language: %s

            ── SEED CODE EXAMPLES ──
            Use these curated examples as stylistic and conceptual anchors. Build related tasks — do not copy them.

            %s
            %s

            Output (STRICT): For each task, the top-level "statement" MUST equal contract.taskStatement exactly (byte-for-byte identical) — write the student-facing statement, then copy it verbatim into taskStatement.

            Generate %d tasks (tiers 1–%d) as specified. %s
            """
            .formatted(
                tierCount,
                context.toString().stripTrailing(),
                language,
                String.join(LIST_SEPARATOR, concepts),
                langLabel,
                seedBlock.toString(),
                buildConstraintSection(constraints, tierCount),
                tierCount, tierCount,
                distractorLine
            );

        messages.add(
            mapper.createObjectNode()
                .put(ROLE, ROLE_USER)
                .put(CONTENT, prompt)
        );

        json.set(MESSAGES, messages);
        json.set(
            RESPONSE_FORMAT, distractorsEnabled
                ? responseFormatTieredContractsWithDistractors(tierCount)
                : responseFormatTieredContracts(tierCount)
        );
        return json;
    }
}
