package de.uni_passau.fim.se2.application.service.hints;

public record AnalysisFinding(
    String ruleId,       // "F821", "NP_NULL_ON_SOME_PATH", "compiler:not-initialized"
    String message,      // "Undefined name 'x'"
    String severity,     // "error", "warning", "info"
    int startLine,       // Line number in assembled code
    int endLine,
    String tool,         // "ruff", "javac", "spotbugs"
    String category      // "undefined_variable", "null_safety", "dead_code", "syntax"
) {}
