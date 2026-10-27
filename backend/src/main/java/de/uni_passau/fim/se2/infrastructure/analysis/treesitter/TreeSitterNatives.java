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

import io.github.treesitter.jtreesitter.Language;
import io.github.treesitter.jtreesitter.NativeLibraryLookup;
import io.github.treesitter.jtreesitter.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * AP26 — Locates the native libraries jtreesitter needs: the tree-sitter runtime and
 * one shared library per grammar. jtreesitter ships none of them; they are built by
 * {@code scripts/build-treesitter-natives.sh} into one directory, taken from the
 * {@value #DIR_PROPERTY} system property, else the {@value #DIR_ENV} environment
 * variable, else {@code ./native}.
 *
 * <p>Registered as a {@link NativeLibraryLookup} service (see
 * {@code META-INF/services}) so jtreesitter loads the runtime from that directory by
 * absolute path on every OS. A library missing from the directory falls through to
 * the OS library search path.
 */
public final class TreeSitterNatives implements NativeLibraryLookup {

    public static final String DIR_PROPERTY = "codeowl.treesitter.nativeDir";
    public static final String DIR_ENV = "TREESITTER_NATIVE_DIR";

    private static final Logger logger = LoggerFactory.getLogger(TreeSitterNatives.class);

    private static volatile Boolean available;

    /** Instantiated by jtreesitter through {@link java.util.ServiceLoader}. */
    public TreeSitterNatives() {}

    @Override
    public SymbolLookup get(Arena arena) {
        // Called from jtreesitter's static initialiser: throwing here would break the
        // bindings for good, so a missing library yields an empty lookup instead.
        Path library = nativeDir().resolve(System.mapLibraryName("tree-sitter"));
        if (!Files.isRegularFile(library)) {
            return name -> Optional.empty();
        }
        return SymbolLookup.libraryLookup(library, arena);
    }

    public static Path nativeDir() {
        String dir = System.getProperty(DIR_PROPERTY);
        if (dir == null || dir.isBlank()) dir = System.getenv(DIR_ENV);
        if (dir == null || dir.isBlank()) dir = "native";
        return Path.of(dir).toAbsolutePath();
    }

    /**
     * Whether the tree-sitter runtime can be loaded. Checked once; if it fails,
     * jtreesitter's classes cannot be initialised again in this JVM anyway.
     */
    public static boolean isAvailable() {
        if (available == null) {
            synchronized (TreeSitterNatives.class) {
                if (available == null) {
                    available = probe();
                }
            }
        }
        return available;
    }

    private static boolean probe() {
        try (Parser ignored = new Parser()) {
            return true;
        } catch (Throwable e) {
            logger.warn("tree-sitter runtime not found in {} or on the library path ({}). "
                    + "Code metrics fall back to heuristics; run backend/scripts/build-treesitter-natives.sh "
                    + "or set {} to enable them.", nativeDir(), e, DIR_ENV);
            return false;
        }
    }

    /**
     * Loads a grammar by its tree-sitter id, e.g. {@code c_sharp} from
     * {@code tree-sitter-c-sharp} via the symbol {@code tree_sitter_c_sharp}.
     */
    public static Language loadLanguage(String grammarId) {
        String libraryName = System.mapLibraryName("tree-sitter-" + grammarId.replace('_', '-'));
        Path library = nativeDir().resolve(libraryName);
        SymbolLookup symbols = Files.isRegularFile(library)
                ? SymbolLookup.libraryLookup(library, Arena.global())
                : SymbolLookup.libraryLookup(libraryName, Arena.global());
        return Language.load(symbols, "tree_sitter_" + grammarId);
    }
}
