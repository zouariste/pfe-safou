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

# Windows counterpart of build-treesitter-natives.sh: builds the native libraries the code
# analyzer (jtreesitter) needs, the tree-sitter runtime plus one shared library per supported
# grammar, for the machine it runs on. Runs in Windows PowerShell 5.1 and PowerShell 7.
#
#   powershell -ExecutionPolicy Bypass -File scripts\build-treesitter-natives.ps1 [output-dir]
#
# (default output-dir: backend\native). Needs a C compiler that builds MinGW-style DLLs:
# $env:CC, or else the first of gcc, clang and zig on the PATH (MSYS2's MinGW gcc, or zig).
# MSVC's cl is not supported. Downloads with Invoke-WebRequest and unpacks with the tar.exe
# that Windows 10 and later ship. The versions are in treesitter-versions.sh.

param([string]$OutDir = (Join-Path $PSScriptRoot '..\native'))

$ErrorActionPreference = 'Stop'
# Windows PowerShell 5.1 may offer only TLS 1.0, which GitHub refuses, and its progress bar
# slows Invoke-WebRequest down by an order of magnitude.
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor 'Tls12'
$ProgressPreference = 'SilentlyContinue'

$versions = Get-Content (Join-Path $PSScriptRoot 'treesitter-versions.sh')
$runtimeVersion = ($versions | Select-String '^TREE_SITTER_VERSION=(\S+)$').Matches[0].Groups[1].Value
$grammars = @($versions | Where-Object { $_ -match '^tree-sitter-[\w-]+:\S+:\S+$' })

# Without $env:CC, take the first compiler on the PATH. Returned as an array: "zig cc" is two words.
function Find-Compiler {
    if ($env:CC) {
        return , @($env:CC -split '\s+')
    }
    foreach ($candidate in 'gcc', 'clang', 'zig') {
        if (Get-Command $candidate -ErrorAction SilentlyContinue) {
            if ($candidate -eq 'zig') {
                return , @('zig', 'cc')
            }
            return , @($candidate)
        }
    }
    throw 'No C compiler found (tried gcc, clang, zig). Install one or set CC.'
}

$cc = Find-Compiler
$ccExe = $cc[0]
$ccArgs = @($cc | Select-Object -Skip 1)

$onWindows = [Environment]::OSVersion.Platform -eq 'Win32NT'
if ($onWindows) {
    $prefix = ''; $ext = 'dll'; $pic = @()
}
elseif ($IsMacOS) {
    $prefix = 'lib'; $ext = 'dylib'; $pic = @('-fPIC')
}
else {
    $prefix = 'lib'; $ext = 'so'; $pic = @('-fPIC')
}

$OutDir = (New-Item -ItemType Directory -Force $OutDir).FullName
$work = Join-Path ([IO.Path]::GetTempPath()) ('treesitter-' + [guid]::NewGuid())
New-Item -ItemType Directory $work | Out-Null

# repository tag -> extracted source dir
function Get-Source([string]$repo, [string]$tag) {
    $archive = Join-Path $work "$repo.tar.gz"
    Invoke-WebRequest -UseBasicParsing -OutFile $archive `
        -Uri "https://codeload.github.com/tree-sitter/$repo/tar.gz/refs/tags/$tag"
    tar -xzf $archive -C $work
    if ($LASTEXITCODE -ne 0) {
        throw "Could not unpack $repo $tag"
    }
    return Join-Path $work "$repo-$($tag.TrimStart('v'))"
}

function Invoke-Compiler([string[]]$arguments) {
    & $ccExe @ccArgs -std=c11 -O2 @pic -shared @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$ccExe failed with exit code $LASTEXITCODE"
    }
}

try {
    Write-Host "Building tree-sitter $runtimeVersion"
    $src = Get-Source 'tree-sitter' $runtimeVersion
    Invoke-Compiler @(
        '-D_POSIX_C_SOURCE=200112L', '-D_DEFAULT_SOURCE', '-D_DARWIN_C_SOURCE',
        "-I$(Join-Path $src 'lib/include')", "-I$(Join-Path $src 'lib/src')",
        (Join-Path $src 'lib/src/lib.c'), '-o', (Join-Path $OutDir "${prefix}tree-sitter.$ext")
    )

    foreach ($entry in $grammars) {
        $repo, $tag, $dir = $entry -split ':'
        Write-Host "Building $repo $tag"
        $src = Join-Path (Join-Path (Get-Source $repo $tag) $dir) 'src'
        $sources = @(Join-Path $src 'parser.c')
        $scanner = Join-Path $src 'scanner.c'
        if (Test-Path $scanner) {
            $sources += $scanner
        }
        Invoke-Compiler (@("-I$src") + $sources + @('-o', (Join-Path $OutDir "$prefix$repo.$ext")))
    }
}
finally {
    Remove-Item -Recurse -Force $work
}

Write-Host "Native libraries written to $OutDir"
