// AP16: the `#group:` grammar as parsed by ParsonsCodeline in public/jsparsons/parsons.js.
// Keep the marker regex and the prefix rule in sync with it.
const GROUP_MARKER_RE = /#group:(\S+)\s*$/;

/** A cluster's prefix: everything before its last underscore, so `base_1` and `base_2` share `base`. */
function groupPrefix(name: string): string {
    const underscore = name.lastIndexOf("_");
    return underscore > 0 ? name.substring(0, underscore) : name;
}

/**
 * The `#group` names that have nothing to swap with, in order of first appearance.
 *
 * Only clusters sharing a prefix are interchangeable. A name whose prefix has no second cluster
 * therefore stays at its position, which is rarely what the teacher who labelled it meant.
 */
export function findLoneGroups(code: string): string[] {
    const namesByPrefix = new Map<string, Set<string>>();
    for (const line of code.split("\n")) {
        const name = line.match(GROUP_MARKER_RE)?.[1];
        if (!name) continue;
        const prefix = groupPrefix(name);
        const names = namesByPrefix.get(prefix) ?? new Set<string>();
        names.add(name);
        namesByPrefix.set(prefix, names);
    }
    return [...namesByPrefix.values()].filter((names) => names.size === 1).map((names) => [...names][0]);
}
