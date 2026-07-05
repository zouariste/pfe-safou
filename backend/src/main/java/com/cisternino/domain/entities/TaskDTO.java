package com.cisternino.domain.entities;

import lombok.*;

import java.util.*;

/**
 * Represents a task with a statement, solution, and tier level.
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDTO {

    /**
     * The problem statement of the task.
     *
     * @return the task statement
     */
    private String statement;

    private SolveContract contract;

    /**
     * The solution or answer to the task.
     *
     * @return the solution string
     */
    private String solution;

    /**
     * The tier level of the task, indicating its difficulty.
     *
     * @return the tier level as an integer
     */
    private int tierLevel;

    /**
     * The cyclomatic complexity of the task's solution.
     *
     * @return the cyclomatic complexity as an integer
     */
    private int cyclomaticComplexity;

    /**
     * The unique identifier of the task.
     *
     * @return the task ID
     */
    private UUID id;

    /**
     * The unique identifier of the sequence.
     *
     * @return the sequence ID
     */
    private UUID sequenceId;

    /**
     * The number of lines of code in the task's solution.
     *
     * @return the lines of code.
     */
    private int linesOfCode;

    /**
     * Represents the maximum level of nesting in the solution code for the task.*
     * The nesting depth refers to the deepest level of code blocks (e.g., loops,
     * conditionals) present in the task's solution, which can be an indicator
     * of complexity.
     */
    private int maximalNestingDepth;

    /**
     * The number of method calls made within the task's solution implementation.*
     * This value quantifies the total count of distinct method calls present in
     * the provided solution code for the task. It is used as a metric to analyze
     * the complexity and modularity of the solution.
     */
    private int methodCallCount;

    /**
     * The number of distinct identifiers used in the task's solution.*
     * This metric counts the unique variable names, functions, classes, or other identifiers
     * present in the task's solution code. It is used to measure the semantic diversity
     * and complexity of the code.
     */
    private int distinctIdentifierCount;

    /**
     * the Programming language the task is in.
     */
    private String programmingLanguage;

    /**
     * Distractor lines separated by \n. Each line ends with #distractor.
     * NULL when distractors are not enabled or not yet generated.
     */
    private String distractors;

    /**
     * JSON string with the distractor plan from sequence generation.
     * Format: {"type":"LOGIC_FLIP","rationale":"..."}
     * NULL when distractors are not enabled.
     */
    private String distractorPlan;

    /**
     * Whether distractors should be delivered to students.
     * When false, distractors are kept in DB but not sent to non-owners.
     * Default: true (distractors active if present).
     */
    @Builder.Default
    private boolean distractorsEnabled = true;

    /**
     * AP22: Read-only context lines displayed BEFORE the puzzle (imports, class decl, etc.).
     * Stored separately from solution so parsons.js never sees them.
     */
    private String contextBefore;

    /**
     * AP22: Read-only context lines displayed AFTER the puzzle (main method, closing braces, etc.).
     */
    private String contextAfter;

    /**
     * AP31: JSON string with the inline-split plan (fragment decomposition of selected lines).
     * Format: {"splits":[{"lineIndex":4,"fragments":["int i = 0;","i < arr.length;","i++"],
     *          "prefix":"for (","suffix":") {","distractors":[{"text":"i <= arr.length;","type":"OFF_BY_ONE"}]}]}
     * NULL when no split lines are defined. Fragment distractors are intentionally visible
     * (no answer-stripping on delivery).
     */
    private String splitPlan;

}