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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer;
import io.github.treesitter.jtreesitter.Language;
import io.github.treesitter.jtreesitter.Node;
import io.github.treesitter.jtreesitter.Parser;
import io.github.treesitter.jtreesitter.Tree;

/**
 * AP26 — Infrastructure implementation of {@link CodeAnalyzer} using tree-sitter. Supports 9 languages: Java, Python,
 * JavaScript, TypeScript, C, C++, C#, Rust, Go. All analysis runs in-process via the FFM API (jtreesitter) — no
 * external containers or processes needed. Without the native libraries (see {@link TreeSitterNatives}) complexity is
 * zero and change analysis falls back to the line-based heuristic.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.treesitter.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class TreeSitterCodeAnalyzer implements CodeAnalyzer {

    private static final String TOTAL_NODES = "totalNodes";

    private static final Logger log = LoggerFactory.getLogger(TreeSitterCodeAnalyzer.class);

    private final boolean nativesAvailable;

    public TreeSitterCodeAnalyzer() {
        this(TreeSitterNatives.isAvailable());
    }

    TreeSitterCodeAnalyzer(boolean nativesAvailable) {
        this.nativesAvailable = nativesAvailable;
    }

    // ═══════════════════════════════════════════════════════════════
    // CodeAnalyzer interface
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean supportsLanguage(String language) {
        return LanguageRegistry.isSupported(language);
    }

    @Override
    public ComplexityResult computeComplexity(String code, String language) {
        if (code == null || code.isBlank() || !nativesAvailable) {
            return ComplexityResult.zero();
        }

        Language tsLang = LanguageRegistry.resolve(language).orElse(null);
        LanguageRegistry.LanguageConfig config = LanguageRegistry.getConfig(language);
        if (tsLang == null || config == null) {
            log.debug("Unsupported language '{}', returning zero complexity", language);
            return ComplexityResult.zero();
        }

        try (
            Parser parser = new Parser(tsLang);
            Tree tree = parser.parse(code).orElseThrow()
        ) {
            Node root = tree.getRootNode();
            int loc = countLinesOfCode(code);
            int cc = computeCC(root, config);
            int mnd = computeMaxNestingDepth(root, config, 0);
            int mcc = countNodeTypes(root, config.callTypes());
            int dic = countDistinctIdentifiers(root, config.identifierTypes());
            return new ComplexityResult(loc, cc, mnd, mcc, dic);
        }
        catch (RuntimeException e) {
            log.warn("tree-sitter complexity analysis failed for '{}': {}", language, e.getMessage());
            return ComplexityResult.zero();
        }
    }

    @Override
    public AstAnalysisResult analyzeChanges(String originalCode, String editedCode, String language) {
        if (originalCode == null) {
            originalCode = "";
        }
        if (editedCode == null) {
            editedCode = "";
        }
        if (!nativesAvailable) {
            return heuristicAnalysis(originalCode, editedCode);
        }

        Language tsLang = LanguageRegistry.resolve(language).orElse(null);
        LanguageRegistry.LanguageConfig config = LanguageRegistry.getConfig(language);
        if (tsLang == null || config == null) {
            log.debug("Unsupported language '{}', falling back to line-based heuristic", language);
            return heuristicAnalysis(originalCode, editedCode);
        }

        try {
            Map<String, Integer> origProfile = profileCode(originalCode, tsLang, config);
            Map<String, Integer> editProfile = profileCode(editedCode, tsLang, config);
            return compareProfiles(origProfile, editProfile);
        }
        catch (Exception e) {
            log.warn("tree-sitter change analysis failed for '{}': {}", language, e.getMessage());
            return heuristicAnalysis(originalCode, editedCode);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Complexity metrics
    // ═══════════════════════════════════════════════════════════════

    private int countLinesOfCode(String code) {
        // Count non-blank lines
        return (int) code.lines().filter(line -> !line.isBlank()).count();
    }

    /**
     * Cyclomatic Complexity: 1 (baseline) + branching nodes + catch clauses.
     */
    private int computeCC(Node node, LanguageRegistry.LanguageConfig config) {
        int cc = 1;
        cc += countNodeTypes(node, config.branchingTypes());
        cc += countNodeTypes(node, config.catchTypes());
        return cc;
    }

    /**
     * Maximum Nesting Depth: deepest level of nested control structures.
     */
    private int computeMaxNestingDepth(Node node, LanguageRegistry.LanguageConfig config, int currentDepth) {
        String type = node.getType();
        boolean isNesting = config.nestingTypes().contains(type);
        int depth = isNesting ? currentDepth + 1 : currentDepth;
        int maxDepth = depth;

        for (Node child : node.getChildren()) {
            int childMax = computeMaxNestingDepth(child, config, depth);
            maxDepth = Math.max(maxDepth, childMax);
        }
        return maxDepth;
    }

    /**
     * Counts all nodes whose type matches any of the given types.
     */
    private int countNodeTypes(Node node, Set<String> types) {
        int count = types.contains(node.getType()) ? 1 : 0;
        for (Node child : node.getChildren()) {
            count += countNodeTypes(child, types);
        }
        return count;
    }

    /**
     * Counts distinct identifier strings in the AST.
     */
    private int countDistinctIdentifiers(Node node, Set<String> identifierTypes) {
        Set<String> names = new HashSet<>();
        collectIdentifiers(node, identifierTypes, names);
        return names.size();
    }

    private void collectIdentifiers(Node node, Set<String> identifierTypes, Set<String> names) {
        if (identifierTypes.contains(node.getType())) {
            String content = node.getText();
            if (content != null && !content.isBlank()) {
                names.add(content);
            }
        }
        for (Node child : node.getChildren()) {
            collectIdentifiers(child, identifierTypes, names);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Change analysis (AST profiling + comparison)
    // ═══════════════════════════════════════════════════════════════

    private Map<String, Integer> profileCode(
        String code, Language tsLang,
        LanguageRegistry.LanguageConfig config
    ) {
        Map<String, Integer> profile = new LinkedHashMap<>();
        if (code.isBlank()) {
            profile.put(TOTAL_NODES, 0);
            return profile;
        }

        try (
            Parser parser = new Parser(tsLang);
            Tree tree = parser.parse(code).orElseThrow()
        ) {
            Node root = tree.getRootNode();

            profile.put("functions", countNodeTypes(root, config.functionTypes()));
            profile.put("loops", countNodeTypes(root, config.loopTypes()));
            profile.put("conditionals", countNodeTypes(root, config.conditionalTypes()));
            profile.put("calls", countNodeTypes(root, config.callTypes()));
            profile.put("identifiers", countDistinctIdentifiers(root, config.identifierTypes()));
            profile.put(TOTAL_NODES, countAllNodes(root));
        }
        return profile;
    }

    private int countAllNodes(Node node) {
        int count = 1;
        for (Node child : node.getChildren()) {
            count += countAllNodes(child);
        }
        return count;
    }

    // ═══════════════════════════════════════════════════════════════
    // Profile comparison (shared between tree-sitter and heuristic)
    // ═══════════════════════════════════════════════════════════════

    private AstAnalysisResult compareProfiles(
        Map<String, Integer> origProfile,
        Map<String, Integer> editProfile
    ) {
        int nodesAdded = 0;
        int nodesRemoved = 0;
        int nodesModified = 0;
        List<String> changeTypes = new ArrayList<>();

        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(origProfile.keySet());
        allKeys.addAll(editProfile.keySet());
        allKeys.remove(TOTAL_NODES);

        for (String key : allKeys) {
            int origCount = origProfile.getOrDefault(key, 0);
            int editCount = editProfile.getOrDefault(key, 0);
            int delta = editCount - origCount;

            if (delta > 0) {
                nodesAdded += delta;
                changeTypes.add("ADD_" + key.toUpperCase(Locale.ROOT));
            }
            else if (delta < 0) {
                nodesRemoved += Math.abs(delta);
                changeTypes.add("REMOVE_" + key.toUpperCase(Locale.ROOT));
            }
        }

        int origTotal = origProfile.getOrDefault(TOTAL_NODES, 0);
        int editTotal = editProfile.getOrDefault(TOTAL_NODES, 0);

        if (nodesAdded == 0 && nodesRemoved == 0 && origTotal > 0 && editTotal > 0) {
            nodesModified = 1;
            changeTypes.add("MODIFY_CONTENT");
        }

        double structuralScore;
        if (origTotal == 0 && editTotal == 0) {
            structuralScore = 0.0;
        }
        else if (origTotal == 0 || editTotal == 0) {
            structuralScore = 1.0;
        }
        else {
            int totalChanges = nodesAdded + nodesRemoved + nodesModified;
            structuralScore = Math.min(1.0, (double) totalChanges / Math.max(origTotal, 1));
        }

        boolean isDistractor = changeTypes.stream()
            .allMatch(ct -> ct.contains("LITERAL") || ct.contains("CONTENT") || ct.contains("COMMENT"));
        int distractorModified = isDistractor ? nodesModified : 0;

        Map<String, Object> distractorChanges = new LinkedHashMap<>();
        distractorChanges.put("added", 0);
        distractorChanges.put("removed", 0);
        distractorChanges.put("modified", distractorModified);

        return new AstAnalysisResult(
            nodesAdded, nodesRemoved, nodesModified,
            structuralScore, changeTypes, distractorChanges
        );
    }

    /**
     * Fallback line-based heuristic for unsupported languages.
     */
    private AstAnalysisResult heuristicAnalysis(String original, String edited) {
        int origLines = original.isBlank() ? 0 : (int) original.lines().count();
        int editLines = edited.isBlank() ? 0 : (int) edited.lines().count();

        Map<String, Integer> origProfile = Map.of(TOTAL_NODES, origLines);
        Map<String, Integer> editProfile = Map.of(TOTAL_NODES, editLines);
        return compareProfiles(origProfile, editProfile);
    }
}
