/**
 * The bits of CSV handling that are the same wherever we write one.
 *
 * Kept apart from `export.ts` so that modules needing only a comma and a
 * download do not pull in the PDF machinery along with it. That module keeps
 * its own copies on purpose: its escaping is looser than the one below, and
 * tightening it would silently change the bytes of exports people already have.
 */

/**
 * Quotes a field if it carries anything that would otherwise break the row.
 *
 * Semicolons count: a file written with commas is still routinely opened by a
 * spreadsheet configured to split on semicolons, and quoting keeps such a field
 * whole when that happens.
 */
export function escapeCsvField(value: string | number | null | undefined): string {
    if (value == null) return "";
    const s = String(value);
    if (/[",;\t\r\n]/.test(s)) {
        return '"' + s.replace(/"/g, '""') + '"';
    }
    return s;
}

/** Hands the browser a generated file to save. */
export function downloadTextFile(content: string, filename: string, mimeType: string) {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}
