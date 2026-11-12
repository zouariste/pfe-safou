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
# Needs curl, tar and a C compiler ($CC, or else the first of cc, gcc, clang and zig
# found on the PATH). On Windows use build-treesitter-natives.ps1, or run this one from Git Bash or MSYS2. The versions
# are in treesitter-versions.sh. The backend finds the libraries in backend/native when started from backend/, or
# wherever TREESITTER_NATIVE_DIR points.

set -eu

# TREE_SITTER_VERSION and GRAMMARS.
. "$(dirname "$0")/treesitter-versions.sh"

# Without $CC, take the first compiler on the PATH; Git Bash ships none named cc.
if [ -z "${CC:-}" ]; then
    for candidate in cc gcc clang zig; do
        if command -v "$candidate" >/dev/null 2>&1; then
            CC=$candidate
            [ "$candidate" = zig ] && CC="zig cc"
            break
        fi
    done
fi
if [ -z "${CC:-}" ]; then
    echo "No C compiler found (tried cc, gcc, clang, zig). Install one or set CC." >&2
    exit 1
fi
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
