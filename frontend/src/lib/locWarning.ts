import type { LocWarning } from "../types/models";

/**
 * How many times a teacher may generate a sequence for the same inputs when the result has tasks
 * over the line limit. Matches the backend's `app.generation.maxAttempts`, which used to retry
 * automatically; the teacher now decides after each attempt instead.
 */
export const MAX_GENERATION_ATTEMPTS = 3;

/**
 * What the home page does with a freshly generated sequence: open it, or first ask the teacher
 * whether to try again because tasks are over the line limit. Once the attempts are used up the
 * sequence opens, and the sequence page asks whether to keep it.
 */
export function generationOutcome(warning: LocWarning | null | undefined, attempt: number): "open" | "offerRetry" {
    if (!warning || warning.tasks.length === 0) return "open";
    return attempt < MAX_GENERATION_ATTEMPTS ? "offerRetry" : "open";
}

/** Joins the over-long tasks into one phrase, e.g. "tier 2 (27 lines), tier 3 (31 lines)". */
export function describeOverLongTasks(
    warning: LocWarning,
    formatTask: (tier: number, lines: number) => string
): string {
    return warning.tasks.map((task) => formatTask(task.tierLevel, task.linesOfCode)).join(", ");
}

/** Reads a line-limit warning out of router state, which is untyped and may hold anything. */
export function locWarningFromState(state: unknown): LocWarning | null {
    const warning = (state as { locWarning?: unknown } | null)?.locWarning as LocWarning | undefined;
    if (!warning || typeof warning.maxLoc !== "number" || !Array.isArray(warning.tasks) || warning.tasks.length === 0) {
        return null;
    }
    return warning;
}
