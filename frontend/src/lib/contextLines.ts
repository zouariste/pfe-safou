import type React from "react";
import type { SplitEntry } from "../types/models";
import { stripGapMarkers } from "./gapMarkers";
import { stripLineAnnotations } from "./solutionLines";

/**
 * AP22 (context mode): the read-only code above and below a puzzle, and moving lines across
 * the two borders between it and the puzzle.
 *
 * Context is always one contiguous block on each side, so the only lines that can move are the
 * ones at a border: the puzzle's first line up into contextBefore, its last line down into
 * contextAfter, and back. Splits (AP31) are keyed by `solution.split("\n")` index, so every move
 * that changes the puzzle's first lines shifts them, and a line that leaves the puzzle loses its
 * split.
 */
export interface ContextParts {
    contextBefore: string;
    code: string;
    contextAfter: string;
    splits: SplitEntry[];
}

/** Same look as the locked context blocks in SolvePage. */
export const CONTEXT_STYLE: React.CSSProperties = {
    background: "#f8f9fa",
    borderLeft: "3px solid #6c757d",
    fontFamily: "'Courier New', Consolas, monospace",
    fontSize: "0.8rem",
    color: "#495057",
};

/** Blank lines around a block are dropped, leading indentation is kept; blank → null. */
export function normalizeContext(code: string | null | undefined): string | null {
    return code && code.trim() ? code.replace(/^(?:[ \t]*\r?\n)+/, "").trimEnd() : null;
}

const isBlank = (line: string) => line.trim() === "";

/**
 * What a puzzle line looks like once it is context: students see context verbatim, so the
 * annotations js-parsons would have hidden and the gap markers it would have turned into gaps
 * have to go.
 */
function asContextLine(line: string): string {
    return stripLineAnnotations(stripGapMarkers(line));
}

/** Leaves at least one puzzle line: moving the last one out would leave nothing to solve. */
function canGiveUpLine(code: string): boolean {
    return code.split("\n").filter((l) => !isBlank(l)).length > 1;
}

/** The puzzle's first line → the end of contextBefore. Null when that is not possible. */
export function puzzleToBefore(parts: ContextParts): ContextParts | null {
    if (!canGiveUpLine(parts.code)) return null;
    const lines = parts.code.split("\n");
    const first = lines.findIndex((l) => !isBlank(l));
    const removed = first + 1; // the line plus any blank lines above it
    const before = normalizeContext(parts.contextBefore);
    const moved = asContextLine(lines[first]);
    return {
        ...parts,
        contextBefore: before ? `${before}\n${moved}` : moved,
        code: lines.slice(removed).join("\n"),
        splits: parts.splits
            .filter((s) => s.lineIndex >= removed)
            .map((s) => ({ ...s, lineIndex: s.lineIndex - removed })),
    };
}

/** The last line of contextBefore → the start of the puzzle. Null when there is none. */
export function beforeToPuzzle(parts: ContextParts): ContextParts | null {
    const before = normalizeContext(parts.contextBefore);
    if (!before) return null;
    const lines = before.split("\n");
    const moved = lines.pop()!;
    return {
        ...parts,
        contextBefore: lines.join("\n"),
        code: parts.code ? `${moved}\n${parts.code}` : moved,
        splits: parts.splits.map((s) => ({ ...s, lineIndex: s.lineIndex + 1 })),
    };
}

/** The puzzle's last line → the start of contextAfter. Null when that is not possible. */
export function puzzleToAfter(parts: ContextParts): ContextParts | null {
    if (!canGiveUpLine(parts.code)) return null;
    const lines = parts.code.split("\n");
    let last = lines.length - 1;
    while (isBlank(lines[last])) last--;
    const after = normalizeContext(parts.contextAfter);
    const moved = asContextLine(lines[last]);
    return {
        ...parts,
        contextAfter: after ? `${moved}\n${after}` : moved,
        code: lines.slice(0, last).join("\n"),
        splits: parts.splits.filter((s) => s.lineIndex < last),
    };
}

/** The first line of contextAfter → the end of the puzzle. Null when there is none. */
export function afterToPuzzle(parts: ContextParts): ContextParts | null {
    const after = normalizeContext(parts.contextAfter);
    if (!after) return null;
    const lines = after.split("\n");
    const moved = lines.shift()!;
    // Only trailing blank lines go; trailing spaces on the last line belong to it (and its split).
    const code = parts.code.replace(/(?:\r?\n[ \t]*)+$/, "");
    return {
        ...parts,
        contextAfter: lines.join("\n"),
        code: code.trim() ? `${code}\n${moved}` : moved,
    };
}
