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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.treesitter.jtreesitter.InputEncoding;
import io.github.treesitter.jtreesitter.Language;
import io.github.treesitter.jtreesitter.Node;
import io.github.treesitter.jtreesitter.Parser;
import io.github.treesitter.jtreesitter.Tree;

/**
 * Reduces a generated program to the function(s) a task asks for. Some models answer "write this function" with a whole
 * program: in Java the method inside a class next to a {@code main} method, in Python the function followed by example
 * calls or an {@code if __name__ == "__main__":} block. None of that belongs in the puzzle, and it inflates the line
 * count.
 *
 * <p>
 * Imports stay. Code that does not have the expected shape (several classes, an interface, everything inside
 * {@code main}) is left alone: reshaping it could lose the solution.
 */
final class ScaffoldingStripper {

    private ScaffoldingStripper() {
    }

    /**
     * Removes the program scaffolding around the solution.
     *
     * @param code     the code
     * @param language the tree-sitter language
     * @param name     the language's name, e.g. "java" or "python"
     * @return the code without scaffolding, or empty if there was none or it could not safely be removed
     */
    static Optional<String> strip(String code, Language language, String name) {
        try (Parser parser = new Parser(language); Tree tree = parser.parse(code, InputEncoding.UTF_8).orElseThrow()) {
            Node root = tree.getRootNode();
            if (root.hasError()) {
                return Optional.empty();
            }
            return switch (name) {
                case "java" -> unwrapJavaClass(root);
                case "python" -> dropPythonTopLevelCode(code, root);
                default -> Optional.empty();
            };
        }
    }

    /** Takes the members out of the only class, dropping {@code main}, and keeps the imports. */
    private static Optional<String> unwrapJavaClass(Node program) {
        List<String> imports = new ArrayList<>();
        return onlyClass(program, imports).flatMap(cls -> unwrapMembers(cls, imports));
    }

    /**
     * The program's only class, collecting its imports on the way; empty if there is none, more than one, or anything
     * else besides a package declaration and comments.
     */
    private static Optional<Node> onlyClass(Node program, List<String> imports) {
        Node cls = null;
        for (Node child : program.getNamedChildren()) {
            switch (child.getType()) {
                case "import_declaration" -> imports.add(text(child));
                case "package_declaration", "line_comment", "block_comment" -> {
                }
                case "class_declaration" -> {
                    if (cls != null) {
                        return Optional.empty();
                    }
                    cls = child;
                }
                default -> {
                    return Optional.empty();
                }
            }
        }
        return Optional.ofNullable(cls);
    }

    private static Optional<String> unwrapMembers(Node cls, List<String> imports) {
        List<String> members = new ArrayList<>();
        boolean hasMethod = false;
        for (Node member : cls.getChildByFieldName("body").map(Node::getNamedChildren).orElse(List.of())) {
            if (isMainMethod(member)) {
                continue;
            }
            hasMethod |= "method_declaration".equals(member.getType());
            members.add(dedent(text(member), member.getStartPoint().column()));
        }
        if (!hasMethod) {
            return Optional.empty();
        }

        StringBuilder out = new StringBuilder();
        if (!imports.isEmpty()) {
            out.append(String.join("\n", imports)).append("\n\n");
        }
        out.append(String.join("\n\n", members));
        return Optional.of(out.toString());
    }

    private static boolean isMainMethod(Node member) {
        return "method_declaration".equals(member.getType())
            && member.getChildByFieldName("name").map(n -> "main".equals(text(n))).orElse(false);
    }

    /** Removes {@code columns} characters of leading whitespace from every line after the first. */
    private static String dedent(String text, int columns) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int cut = 0;
            while (cut < columns && cut < line.length() && (line.charAt(cut) == ' ' || line.charAt(cut) == '\t')) {
                cut++;
            }
            out.append('\n').append(line.substring(cut));
        }
        return out.toString();
    }

    /** Drops {@code if __name__ == "__main__":} blocks and top-level calls such as {@code print(f(3))}. */
    private static Optional<String> dropPythonTopLevelCode(String code, Node module) {
        List<int[]> ranges = new ArrayList<>();
        boolean hasFunction = false;
        for (Node child : module.getNamedChildren()) {
            hasFunction |= "function_definition".equals(child.getType())
                || "decorated_definition".equals(child.getType());
            if (isMainGuard(child) || isTopLevelCall(child)) {
                ranges.add(new int[] { child.getStartByte(), child.getEndByte() });
            }
        }
        if (ranges.isEmpty() || !hasFunction) {
            return Optional.empty();
        }
        String stripped = CommentStripper.removeRanges(code.getBytes(StandardCharsets.UTF_8), ranges);
        // Removed statements can leave blank lines on both sides; keep at most PEP 8's two.
        return Optional.of(stripped.replaceAll("\n{4,}", "\n\n\n").strip());
    }

    private static boolean isMainGuard(Node node) {
        return "if_statement".equals(node.getType())
            && node.getChildByFieldName("condition").map(c -> text(c).contains("__name__")).orElse(false);
    }

    /** A node's source text; tree-sitter returns null when it has none. */
    private static String text(Node node) {
        String text = node.getText();
        return text == null ? "" : text;
    }

    private static boolean isTopLevelCall(Node node) {
        return "expression_statement".equals(node.getType()) && node.getNamedChildCount() == 1
            && node.getNamedChild(0).map(c -> "call".equals(c.getType())).orElse(false);
    }
}
