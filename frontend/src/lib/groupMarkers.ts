import { stripLineAnnotations } from "./solutionLines";

// AP16: the `#group:` grammar as parsed by ParsonsCodeline in public/jsparsons/parsons.js.
// Keep the marker regex and the prefix rule in sync with it.
const GROUP_MARKER_RE = /#group:(\S+)\s*$/;

/** A cluster's prefix: everything before its last underscore, so `base_1` and `base_2` share `base`. */
function groupPrefix(name: string): string {
    const underscore = name.lastIndexOf("_");
    return underscore > 0 ? name.substring(0, underscore) : name;
}

/**
 * The `#group` name of every line that becomes part of the solution, or null for an unmarked one.
 * Blank lines and distractors are not part of it, so they cannot split a cluster.
 */
function solutionGroupNames(code: string): (string | null)[] {
    return code
        .split("\n")
        .filter((line) => !/#distractor\s*$/.test(line) && stripLineAnnotations(line).trim() !== "")
        .map((line) => line.match(GROUP_MARKER_RE)?.[1] ?? null);
}

/** The cluster names in `names`, grouped by prefix, in order of first appearance. */
function namesByPrefix(names: (string | null)[]): string[][] {
    const prefixes = new Map<string, Set<string>>();
    for (const name of names) {
        if (!name) continue;
        const prefix = groupPrefix(name);
        prefixes.set(prefix, (prefixes.get(prefix) ?? new Set<string>()).add(name));
    }
    return [...prefixes.values()].map((cluster) => [...cluster]);
}

/**
 * The `#group` names that have nothing to swap with, in order of first appearance.
 *
 * Only clusters sharing a prefix are interchangeable. A name whose prefix has no second cluster
 * therefore stays at its position, which is rarely what the teacher who labelled it meant.
 */
export function findLoneGroups(code: string): string[] {
    return namesByPrefix(solutionGroupNames(code))
        .filter((names) => names.length === 1)
        .map((names) => names[0]);
}

/**
 * The `#group` names whose lines are not directly next to each other, in order of first appearance.
 *
 * Such a cluster has no single place another could take, so the grader keeps every cluster of its
 * prefix position-fixed. A lone group is left out: it could not swap anyway, and
 * {@link findLoneGroups} already reports it.
 */
export function findSplitGroups(code: string): string[] {
    const names = solutionGroupNames(code);
    return namesByPrefix(names)
        .filter((cluster) => cluster.length > 1)
        .flat()
        .filter((name) => names.lastIndexOf(name) - names.indexOf(name) + 1 !== names.filter((n) => n === name).length)
        .sort((a, b) => names.indexOf(a) - names.indexOf(b));
}
