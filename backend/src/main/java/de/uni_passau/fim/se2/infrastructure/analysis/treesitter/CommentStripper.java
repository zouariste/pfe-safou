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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.github.treesitter.jtreesitter.InputEncoding;
import io.github.treesitter.jtreesitter.Language;
import io.github.treesitter.jtreesitter.Node;
import io.github.treesitter.jtreesitter.Parser;
import io.github.treesitter.jtreesitter.Tree;

/**
 * Removes comments using the syntax tree, so comment markers inside strings ({@code "#ff0000"}, {@code "http://..."})
 * are left alone. A line that held only a comment is dropped rather than left blank, and whitespace that preceded an
 * end-of-line comment goes with it. In Python, docstrings go too, unless one is the only statement of its body, where
 * removing it would leave the body empty.
 */
final class CommentStripper {

    private CommentStripper() {
    }

    /**
     * Removes the comments from code.
     *
     * @param code     the code
     * @param language the tree-sitter language to parse it with
     * @param python   whether to remove docstrings as well
     * @return the code without comments, with leading and trailing whitespace removed
     */
    static String strip(String code, Language language, boolean python) {
        byte[] bytes = code.getBytes(StandardCharsets.UTF_8);
        List<int[]> ranges = new ArrayList<>();
        try (Parser parser = new Parser(language); Tree tree = parser.parse(code, InputEncoding.UTF_8).orElseThrow()) {
            collect(tree.getRootNode(), python, ranges);
        }
        if (ranges.isEmpty()) {
            return code.strip();
        }
        return removeRanges(bytes, ranges).strip();
    }

    private static void collect(Node node, boolean python, List<int[]> ranges) {
        if (node.getType().contains("comment") || python && isDocstring(node)) {
            ranges.add(new int[] { node.getStartByte(), node.getEndByte() });
            return;
        }
        for (Node child : node.getChildren()) {
            collect(child, python, ranges);
        }
    }

    /** A string statement first in a module, class or function body that has other statements after it. */
    private static boolean isDocstring(Node node) {
        if (
            !"expression_statement".equals(node.getType()) || node.getNamedChildCount() != 1
                || !node.getNamedChild(0).map(c -> c.getType().contains("string")).orElse(false)
        ) {
            return false;
        }
        return node.getParent()
            .filter(p -> "module".equals(p.getType()) || "block".equals(p.getType()))
            .filter(p -> p.getNamedChildCount() > 1)
            .flatMap(p -> p.getNamedChild(0))
            .map(first -> first.getStartByte() == node.getStartByte())
            .orElse(false);
    }

    /**
     * Cuts the ranges out line by line. Lines that lost something have trailing whitespace removed, and are dropped
     * when nothing is left; lines that were blank to begin with stay.
     */
    static String removeRanges(byte[] bytes, List<int[]> ranges) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
        int lineStart = 0;
        while (lineStart <= bytes.length) {
            int lineEnd = lineStart;
            while (lineEnd < bytes.length && bytes[lineEnd] != '\n') {
                lineEnd++;
            }
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            boolean cut = false;
            for (int i = lineStart; i < lineEnd; i++) {
                if (inRange(i, ranges)) {
                    cut = true;
                }
                else {
                    line.write(bytes[i]);
                }
            }
            String text = line.toString(StandardCharsets.UTF_8);
            if (cut) {
                text = text.stripTrailing();
            }
            boolean last = lineEnd >= bytes.length;
            if (!(cut && text.isBlank())) {
                out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
                if (!last) {
                    out.write('\n');
                }
            }
            lineStart = lineEnd + 1;
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static boolean inRange(int index, List<int[]> ranges) {
        for (int[] range : ranges) {
            if (index >= range[0] && index < range[1]) {
                return true;
            }
        }
        return false;
    }
}
