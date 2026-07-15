package de.uni_passau.fim.se2.domain.entities;

/**
 * Enum representing different types of prompts used for distinguishing between
 * programming concepts and sample tasks.
 * <p>
 * The prompts follow a structured naming convention:
 * <ul>
 *   <li>CS - Both Concepts and Sample Task are provided.</li>
 *   <li>NOT_CS - Concepts are NOT given, but a Sample Task is provided.</li>
 *   <li>C_NOT_S - Concepts are given, but a Sample Task is NOT provided.</li>
 *   <li>SOLVE - Represents a solving prompt.</li>
 * </ul>
 * </p>
 * @author Luca Cisternino
 * @version 1.0
 */
public enum PromptType {

    /** Both Concepts and Sample Task are provided. */
    CS,

    /** Concepts are NOT given, but a Sample Task is provided. */
    NOT_CS,

    /** Concepts are given, but a Sample Task is NOT provided. */
    C_NOT_S,

    /** Represents a solving prompt. */
    SOLVE
}