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

# The tree-sitter versions build-treesitter-natives.sh and build-treesitter-natives.ps1
# build. Sourced by the former and read line by line by the latter, so keep the format:
# one NAME=value line for the runtime and one repository:tag:grammar-dir line per grammar.

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
