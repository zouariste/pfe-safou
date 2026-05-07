package com.cisternino;

import java.util.List;

/**
 * Represents a set of problem statements.
 * <p>
 * This class encapsulates a list of problem statements that can be accessed via a getter method.
 * </p>
 *
 * @author Luca Cisternino
 * @version 1.0
 */
public class ProblemSet {
    
    /**
     * A list containing problem statements.
     */
    private List<String> problem_statements;

    /**
     * Retrieves the list of problem statements.
     *
     * @return a list of problem statements
     */
    public List<String> getProblemStatements() {
        return problem_statements;
    }
}
