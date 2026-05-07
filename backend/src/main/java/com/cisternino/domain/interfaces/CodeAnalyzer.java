package com.cisternino.domain.interfaces;

import java.util.List;
import java.util.Map;

/**
 * AP26 — Domain interface for language-agnostic code analysis.
 * Implementations live in the infrastructure layer (e.g. tree-sitter).
 */
public interface CodeAnalyzer {

    /**
     * Computes complexity metrics for a code snippet in the given language.
     */
    ComplexityResult computeComplexity(String code, String language);

    /**
     * Compares two code versions and returns structural change metrics.
     */
    AstAnalysisResult analyzeChanges(String originalCode, String editedCode, String language);

    /**
     * Returns true if this analyzer supports the given language.
     */
    boolean supportsLanguage(String language);

    // ── Result records ──

    record ComplexityResult(
            int linesOfCode,
            int cyclomaticComplexity,
            int maxNestingDepth,
            int methodCallCount,
            int distinctIdentifierCount
    ) {
        public static ComplexityResult zero() {
            return new ComplexityResult(0, 0, 0, 0, 0);
        }
    }

    record AstAnalysisResult(
            int nodesAdded,
            int nodesRemoved,
            int nodesModified,
            double structuralScore,
            List<String> changeTypes,
            Map<String, Object> distractorChanges
    ) {}
}
