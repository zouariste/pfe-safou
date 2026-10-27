// Converts ESLint's JSON output into a GitLab Code Quality report, which GitLab shows in the merge request widget,
// and prints the findings, since `--format json` replaces ESLint's own console output.
//
//   node eslint.mjs <eslint.json> <out.json> <repository root>
//
// A missing ESLint report (ESLint crashed before writing it) produces an empty Code Quality report, so the job fails
// for its real reason and not this one.
import { createHash } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { relative, resolve, sep } from "node:path";

const [input, output, root] = process.argv.slice(2);
if (!input || !output || !root) {
    console.error("usage: node eslint.mjs <eslint.json> <out.json> <root>");
    process.exit(2);
}

const results = existsSync(input) ? JSON.parse(readFileSync(input, "utf8")) : [];
if (!existsSync(input)) console.error(`No ESLint report at ${input}; writing an empty Code Quality report.`);

const repoRoot = resolve(root);
const seen = new Map();
const issues = [];
for (const result of results) {
    const path = relative(repoRoot, result.filePath).split(sep).join("/");
    for (const msg of result.messages) {
        const rule = msg.ruleId ?? "parse-error";
        const severity = msg.fatal ? "critical" : msg.severity === 2 ? "major" : "minor";
        console.log(`${path}:${msg.line ?? 1}:${msg.column ?? 1}  ${msg.severity === 2 ? "error" : "warning"}  ${msg.message}  ${rule}`);
        // No line number in the fingerprint, so a finding keeps it when code above it moves; identical findings
        // in one file are told apart by order.
        const key = `eslint:${rule}|${path}|${msg.message}`;
        const occurrence = (seen.get(key) ?? 0) + 1;
        seen.set(key, occurrence);
        issues.push({
            description: msg.message,
            check_name: `eslint:${rule}`,
            fingerprint: createHash("sha256").update(`${key}|${occurrence}`).digest("hex"),
            severity,
            location: { path, lines: { begin: msg.line ?? 1 } },
        });
    }
}

writeFileSync(output, JSON.stringify(issues, null, 2) + "\n");
const errors = results.reduce((n, r) => n + r.errorCount, 0);
const warnings = results.reduce((n, r) => n + r.warningCount, 0);
console.log(`\n${errors} error(s), ${warnings} warning(s); wrote ${issues.length} finding(s) to ${output}`);
