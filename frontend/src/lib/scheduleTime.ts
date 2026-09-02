/**
 * Conversion between an ISO instant and the value a `datetime-local` input wants.
 *
 * The input has no timezone: it shows and returns wall-clock time in whatever
 * zone the browser is in. The server stores instants. These two functions are
 * the boundary, and getting them wrong shifts every deadline by the offset —
 * which is why they are here rather than inline, and tested.
 */

/** ISO instant to the `YYYY-MM-DDTHH:mm` a datetime-local input displays. */
export function toLocalInput(iso: string | null | undefined): string {
    if (!iso) return "";
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) return "";

    // Subtracting the offset before formatting as UTC yields local wall-clock,
    // which is what the input expects. toISOString would give UTC and display
    // the wrong time to anyone not on UTC.
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
    return local.toISOString().slice(0, 16);
}

/** A datetime-local value back to an ISO instant, or null when empty. */
export function fromLocalInput(value: string | null | undefined): string | null {
    if (!value) return null;
    // new Date on a datetime-local string interprets it as local time, which is
    // what the teacher meant when they typed it.
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return null;
    return date.toISOString();
}
