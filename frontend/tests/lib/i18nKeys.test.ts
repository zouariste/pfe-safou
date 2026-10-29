import { readdirSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { describe, expect, it } from "vitest";
import en from "../../src/i18n/en.json";

/**
 * Every literal key passed to t() must exist in the bundle.
 *
 * <p>Written after the manual creator's context-mode keys were inserted into the wrong section
 * of both files: the bundles still matched each other (see i18nEncoding.test.ts), so the page
 * rendered "manual.contextMode" instead of text. Pages mock i18n in their tests, so only a check
 * against the source itself catches this.
 */
const SRC = resolve(__dirname, "../../src");
const KEY_CALL = /\bt\(\s*"([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)"/g;

function sourceFiles(dir: string): string[] {
    return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
        const full = join(dir, entry.name);
        if (entry.isDirectory()) return sourceFiles(full);
        return /\.tsx?$/.test(entry.name) ? [full] : [];
    });
}

function has(bundle: unknown, key: string): boolean {
    let value = bundle;
    for (const part of key.split(".")) {
        if (!value || typeof value !== "object") return false;
        value = (value as Record<string, unknown>)[part];
    }
    return value !== undefined;
}

describe("t() keys", () => {
    it("all exist in en.json (de.json is held to the same keys elsewhere)", () => {
        const files = sourceFiles(SRC);
        const missing = files.flatMap((file) =>
            [...readFileSync(file, "utf8").matchAll(KEY_CALL)]
                .map((m) => m[1])
                // plural keys live under key_one / key_other
                .filter((key) => !has(en, key) && !has(en, `${key}_one`))
                .map((key) => `${file.slice(SRC.length + 1)}: ${key}`)
        );

        expect(files.length).toBeGreaterThan(20);
        expect(missing).toEqual([]);
    });
});
