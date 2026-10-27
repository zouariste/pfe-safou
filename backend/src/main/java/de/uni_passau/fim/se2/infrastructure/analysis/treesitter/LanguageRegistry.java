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
package de.uni_passau.fim.se2.infrastructure.analysis.treesitter;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.treesitter.jtreesitter.Language;

/**
 * AP26 — Maps user-facing language names to tree-sitter grammars ({@link Language}) and provides node-type mappings for
 * each language so that a single traversal algorithm can compute metrics across all supported languages.
 */
public final class LanguageRegistry {

    private static final String DO_STATEMENT = "do_statement";
    private static final String FOR_STATEMENT = "for_statement";
    private static final String WHILE_STATEMENT = "while_statement";
    private static final String IF_STATEMENT = "if_statement";
    private static final String TRY_STATEMENT = "try_statement";
    private static final String IDENTIFIER = "identifier";
    private static final String CATCH_CLAUSE = "catch_clause";
    private static final String FOR_IN_STATEMENT = "for_in_statement";
    private static final String SWITCH_STATEMENT = "switch_statement";
    private static final String CALL_EXPRESSION = "call_expression";
    private static final String JAVASCRIPT = "javascript";
    private static final String TYPESCRIPT = "typescript";
    private static final String CPP = "cpp";
    private static final String GO = "go";

    private static final Logger log = LoggerFactory.getLogger(LanguageRegistry.class);

    private LanguageRegistry() {
    }

    // ── Language resolution ──

    /** User-facing name → tree-sitter grammar id (see {@link TreeSitterNatives#loadLanguage}). */
    private static final Map<String, String> LANGUAGE_MAP = new HashMap<>();

    static {
        LANGUAGE_MAP.put("java", "java");
        LANGUAGE_MAP.put("python", "python");
        LANGUAGE_MAP.put(JAVASCRIPT, JAVASCRIPT);
        LANGUAGE_MAP.put("js", JAVASCRIPT);
        LANGUAGE_MAP.put(TYPESCRIPT, TYPESCRIPT);
        LANGUAGE_MAP.put("ts", TYPESCRIPT);
        LANGUAGE_MAP.put("c", "c");
        LANGUAGE_MAP.put("c++", CPP);
        LANGUAGE_MAP.put(CPP, CPP);
        LANGUAGE_MAP.put("c#", "c_sharp");
        LANGUAGE_MAP.put("csharp", "c_sharp");
        LANGUAGE_MAP.put("cs", "c_sharp");
        LANGUAGE_MAP.put("rust", "rust");
        LANGUAGE_MAP.put(GO, GO);
        LANGUAGE_MAP.put("golang", GO);
    }

    /** Grammars are loaded on first use; a failed load is cached as empty and logged once. */
    private static final Map<String, Optional<Language>> LOADED = new ConcurrentHashMap<>();

    /**
     * Looks up the tree-sitter grammar for a language name, loading it on first use.
     *
     * @param language the programming language, in any of its known spellings
     * @return the grammar, or empty if the language is unknown or its grammar could not be loaded
     */
    public static Optional<Language> resolve(String language) {
        String grammarId = grammarId(language);
        if (grammarId == null) {
            return Optional.empty();
        }
        return LOADED.computeIfAbsent(grammarId, LanguageRegistry::load);
    }

    private static Optional<Language> load(String grammarId) {
        try {
            return Optional.of(TreeSitterNatives.loadLanguage(grammarId));
        }
        catch (Throwable e) {
            log.warn(
                "Could not load tree-sitter grammar '{}' from {}: {}",
                grammarId, TreeSitterNatives.nativeDir(), e.toString()
            );
            return Optional.empty();
        }
    }

    private static String grammarId(String language) {
        if (language == null) {
            return null;
        }
        return LANGUAGE_MAP.get(language.toLowerCase(Locale.ROOT).trim());
    }

    public static boolean isSupported(String language) {
        return grammarId(language) != null;
    }

    // ── Node-type classifications per language ──
    // Each set tells the traversal which AST node types count towards a metric.

    public record LanguageConfig(
        Set<String> functionTypes,
        Set<String> loopTypes,
        Set<String> conditionalTypes,
        Set<String> branchingTypes,   // CC: adds +1 per occurrence
        Set<String> nestingTypes,     // MND: tracked for nesting depth
        Set<String> callTypes,        // MCC: method/function call expressions
        Set<String> identifierTypes,  // DIC: distinct identifier names
        Set<String> catchTypes        // CC: catch clauses
    ) {
    }

    private static final Map<String, LanguageConfig> CONFIGS = new HashMap<>();

    static {
        // ── Java ──
        CONFIGS.put(
            "java", new LanguageConfig(
                Set.of("method_declaration", "constructor_declaration"),
                Set.of(FOR_STATEMENT, "enhanced_for_statement", WHILE_STATEMENT, DO_STATEMENT),
                Set.of(IF_STATEMENT, "switch_expression"),
                Set.of(
                    IF_STATEMENT, FOR_STATEMENT, "enhanced_for_statement", WHILE_STATEMENT,
                    DO_STATEMENT, "switch_expression", "case_clause"
                ),
                Set.of(
                    IF_STATEMENT, FOR_STATEMENT, "enhanced_for_statement", WHILE_STATEMENT,
                    DO_STATEMENT, "switch_expression", TRY_STATEMENT, "synchronized_statement"
                ),
                Set.of("method_invocation"),
                Set.of(IDENTIFIER),
                Set.of(CATCH_CLAUSE)
            )
        );

        // ── Python ──
        CONFIGS.put(
            "python", new LanguageConfig(
                Set.of("function_definition"),
                Set.of(FOR_STATEMENT, WHILE_STATEMENT),
                Set.of(IF_STATEMENT, "elif_clause"),
                Set.of(
                    IF_STATEMENT, "elif_clause", FOR_STATEMENT, WHILE_STATEMENT,
                    "conditional_expression"
                ),
                Set.of(IF_STATEMENT, FOR_STATEMENT, WHILE_STATEMENT, TRY_STATEMENT, "with_statement"),
                Set.of("call"),
                Set.of(IDENTIFIER),
                Set.of("except_clause")
            )
        );

        // ── JavaScript ──
        CONFIGS.put(
            JAVASCRIPT, new LanguageConfig(
                Set.of("function_declaration", "method_definition", "arrow_function"),
                Set.of(FOR_STATEMENT, FOR_IN_STATEMENT, WHILE_STATEMENT, DO_STATEMENT),
                Set.of(IF_STATEMENT, SWITCH_STATEMENT),
                Set.of(
                    IF_STATEMENT, FOR_STATEMENT, FOR_IN_STATEMENT, WHILE_STATEMENT,
                    DO_STATEMENT, "switch_case", "ternary_expression"
                ),
                Set.of(
                    IF_STATEMENT, FOR_STATEMENT, FOR_IN_STATEMENT, WHILE_STATEMENT,
                    DO_STATEMENT, SWITCH_STATEMENT, TRY_STATEMENT
                ),
                Set.of(CALL_EXPRESSION),
                Set.of(IDENTIFIER),
                Set.of(CATCH_CLAUSE)
            )
        );

        // ── TypeScript (extends JS) ──
        CONFIGS.put(
            TYPESCRIPT, new LanguageConfig(
                Set.of("function_declaration", "method_definition", "arrow_function"),
                Set.of(FOR_STATEMENT, FOR_IN_STATEMENT, WHILE_STATEMENT, DO_STATEMENT),
                Set.of(IF_STATEMENT, SWITCH_STATEMENT),
                Set.of(
                    IF_STATEMENT, FOR_STATEMENT, FOR_IN_STATEMENT, WHILE_STATEMENT,
                    DO_STATEMENT, "switch_case", "ternary_expression"
                ),
                Set.of(
                    IF_STATEMENT, FOR_STATEMENT, FOR_IN_STATEMENT, WHILE_STATEMENT,
                    DO_STATEMENT, SWITCH_STATEMENT, TRY_STATEMENT
                ),
                Set.of(CALL_EXPRESSION),
                Set.of(IDENTIFIER, "type_identifier"),
                Set.of(CATCH_CLAUSE)
            )
        );

        // ── C ──
        CONFIGS.put(
            "c", new LanguageConfig(
                Set.of("function_definition"),
                Set.of(FOR_STATEMENT, WHILE_STATEMENT, DO_STATEMENT),
                Set.of(IF_STATEMENT, SWITCH_STATEMENT),
                Set.of(IF_STATEMENT, FOR_STATEMENT, WHILE_STATEMENT, DO_STATEMENT, "case_statement"),
                Set.of(IF_STATEMENT, FOR_STATEMENT, WHILE_STATEMENT, DO_STATEMENT, SWITCH_STATEMENT),
                Set.of(CALL_EXPRESSION),
                Set.of(IDENTIFIER),
                Set.of()
            )
        );

        // ── C++ ──
        CONFIGS.put(
            CPP, new LanguageConfig(
                Set.of("function_definition"),
                Set.of(FOR_STATEMENT, "for_range_loop", WHILE_STATEMENT, DO_STATEMENT),
                Set.of(IF_STATEMENT, SWITCH_STATEMENT),
                Set.of(
                    IF_STATEMENT, FOR_STATEMENT, "for_range_loop", WHILE_STATEMENT,
                    DO_STATEMENT, "case_statement"
                ),
                Set.of(
                    IF_STATEMENT, FOR_STATEMENT, "for_range_loop", WHILE_STATEMENT,
                    DO_STATEMENT, SWITCH_STATEMENT, TRY_STATEMENT
                ),
                Set.of(CALL_EXPRESSION),
                Set.of(IDENTIFIER),
                Set.of(CATCH_CLAUSE)
            )
        );

        // ── C# ──
        CONFIGS.put(
            "csharp", new LanguageConfig(
                Set.of("method_declaration", "constructor_declaration", "local_function_statement"),
                Set.of(FOR_STATEMENT, "foreach_statement", WHILE_STATEMENT, DO_STATEMENT),
                Set.of(IF_STATEMENT, SWITCH_STATEMENT),
                Set.of(
                    IF_STATEMENT, FOR_STATEMENT, "foreach_statement", WHILE_STATEMENT,
                    DO_STATEMENT, "switch_section"
                ),
                Set.of(
                    IF_STATEMENT, FOR_STATEMENT, "foreach_statement", WHILE_STATEMENT,
                    DO_STATEMENT, SWITCH_STATEMENT, TRY_STATEMENT
                ),
                Set.of("invocation_expression"),
                Set.of(IDENTIFIER),
                Set.of(CATCH_CLAUSE)
            )
        );

        // ── Rust ──
        CONFIGS.put(
            "rust", new LanguageConfig(
                Set.of("function_item"),
                Set.of("for_expression", "while_expression", "loop_expression"),
                Set.of("if_expression", "match_expression"),
                Set.of(
                    "if_expression", "for_expression", "while_expression", "loop_expression",
                    "match_arm"
                ),
                Set.of(
                    "if_expression", "for_expression", "while_expression", "loop_expression",
                    "match_expression", "unsafe_block"
                ),
                Set.of(CALL_EXPRESSION),
                Set.of(IDENTIFIER),
                Set.of()
            )
        );

        // ── Go ──
        CONFIGS.put(
            GO, new LanguageConfig(
                Set.of("function_declaration", "method_declaration"),
                Set.of(FOR_STATEMENT),
                Set.of(IF_STATEMENT, "expression_switch_statement", "type_switch_statement"),
                Set.of(IF_STATEMENT, FOR_STATEMENT, "expression_case"),
                Set.of(
                    IF_STATEMENT, FOR_STATEMENT, "expression_switch_statement",
                    "type_switch_statement", "select_statement"
                ),
                Set.of(CALL_EXPRESSION),
                Set.of(IDENTIFIER),
                Set.of()
            )
        );

        // aliases
        CONFIGS.put("js", CONFIGS.get(JAVASCRIPT));
        CONFIGS.put("ts", CONFIGS.get(TYPESCRIPT));
        CONFIGS.put("c++", CONFIGS.get(CPP));
        CONFIGS.put("c#", CONFIGS.get("csharp"));
        CONFIGS.put("cs", CONFIGS.get("csharp"));
        CONFIGS.put("golang", CONFIGS.get(GO));
    }

    public static LanguageConfig getConfig(String language) {
        if (language == null) {
            return null;
        }
        return CONFIGS.get(language.toLowerCase(Locale.ROOT).trim());
    }
}
