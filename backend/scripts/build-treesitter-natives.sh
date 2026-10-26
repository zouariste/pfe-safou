#!/bin/sh
#
# Copyright (C) 2026 CodeOwl contributors
#
# This file is part of CodeOwl.
#
# CodeOwl is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or (at
# your option) any later version.
#
# CodeOwl is distributed in the hope that it will be useful, but
# WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with CodeOwl. If not, see <http://www.gnu.org/licenses/>.
#

# Builds the native libraries the code analyzer (jtreesitter) needs: the tree-sitter
# runtime plus one shared library per supported grammar, for the machine it runs on.
#
#   scripts/build-treesitter-natives.sh [output-dir]     (default: backend/native)
#
# Needs curl, tar and a C compiler ($CC, default cc). On Windows run it from Git Bash
# or MSYS2 with MinGW gcc or zig (CC="zig cc"). The backend finds the libraries in
# backend/native when started from backend/, or wherever TREESITTER_NATIVE_DIR points.

set -eu

# The runtime must match the jtreesitter version in pom.xml (0.26.x).
TREE_SITTER_VERSION=v0.26.13

# repository:tag:grammar-dir; the library and symbol names follow the repository name.
GRAMMARS="
tree-sitter-java:v0.23.5:.
tree-sitter-python:v0.25.0:.
tree-sitter-javascript:v0.25.0:.
tree-sitter-typescript:v0.23.2:typescript
tree-sitter-c:v0.24.2:.
tree-sitter-cpp:v0.23.4:.
tree-sitter-c-sharp:v0.23.5:.
tree-sitter-rust:v0.24.2:.
tree-sitter-go:v0.25.0:.
"

CC=${CC:-cc}
OUT=${1:-$(cd "$(dirname "$0")/.." && pwd)/native}

case "$(uname -s)" in
    Darwin)               PREFIX=lib; EXT=dylib ;;
    MINGW*|MSYS*|CYGWIN*) PREFIX=;    EXT=dll ;;
    *)                    PREFIX=lib; EXT=so ;;
esac

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)

fetch() { # repository tag -> extracted source dir
    curl -fsSL "https://codeload.github.com/tree-sitter/$1/tar.gz/refs/tags/$2" | tar -xz -C "$WORK"
    echo "$WORK/$1-${2#v}"
}

echo "Building tree-sitter $TREE_SITTER_VERSION"
src=$(fetch tree-sitter "$TREE_SITTER_VERSION")
$CC -std=c11 -O2 -fPIC -shared \
    -D_POSIX_C_SOURCE=200112L -D_DEFAULT_SOURCE -D_DARWIN_C_SOURCE \
    -I"$src/lib/include" -I"$src/lib/src" \
    "$src/lib/src/lib.c" -o "$OUT/${PREFIX}tree-sitter.$EXT"

for entry in $GRAMMARS; do
    repo=${entry%%:*}; rest=${entry#*:}
    tag=${rest%%:*}; dir=${rest#*:}
    echo "Building $repo $tag"
    src=$(fetch "$repo" "$tag")/$dir/src
    scanner=
    [ -f "$src/scanner.c" ] && scanner="$src/scanner.c"
    # shellcheck disable=SC2086
    $CC -std=c11 -O2 -fPIC -shared -I"$src" "$src/parser.c" $scanner \
        -o "$OUT/${PREFIX}$repo.$EXT"
done

echo "Native libraries written to $OUT"
