import { describe, it, expect } from "vitest";
import { toLocalInput, fromLocalInput } from "../../src/lib/scheduleTime.ts";

/**
 * A datetime-local input has no timezone: it shows and returns wall-clock time
 * in the browser's zone, while the server stores instants. Getting this
 * boundary wrong shifts every deadline by the offset, silently, and only for
 * people not on UTC — so it is worth pinning rather than trusting.
 */
describe("schedule time conversion", () => {
    it("round-trips an instant through the input and back", () => {
        const iso = "2026-09-02T14:30:00.000Z";
        const roundTripped = fromLocalInput(toLocalInput(iso));
        expect(roundTripped).toBe(iso);
    });

    it("round-trips several instants across the year", () => {
        // Includes dates either side of a European DST switch, where a naive
        // fixed-offset conversion drifts by an hour.
        for (const iso of [
            "2026-01-15T08:00:00.000Z",
            "2026-03-29T01:30:00.000Z",
            "2026-06-21T23:59:00.000Z",
            "2026-10-25T02:15:00.000Z",
            "2026-12-31T23:00:00.000Z",
        ]) {
            expect(fromLocalInput(toLocalInput(iso))).toBe(iso);
        }
    });

    it("produces the shape a datetime-local input expects", () => {
        const value = toLocalInput("2026-09-02T14:30:00.000Z");
        expect(value).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    });

    it("shows local wall-clock time, not UTC", () => {
        const iso = "2026-09-02T14:30:00.000Z";
        const local = toLocalInput(iso);
        const expected = new Date(iso);
        expect(local.slice(11, 13)).toBe(String(expected.getHours()).padStart(2, "0"));
        expect(local.slice(14, 16)).toBe(String(expected.getMinutes()).padStart(2, "0"));
    });

    it("treats an empty field as no constraint", () => {
        // Empty means "available at once" / "open indefinitely", so it must
        // become null rather than an epoch date.
        expect(fromLocalInput("")).toBeNull();
        expect(fromLocalInput(null)).toBeNull();
        expect(fromLocalInput(undefined)).toBeNull();
        expect(toLocalInput(null)).toBe("");
        expect(toLocalInput(undefined)).toBe("");
        expect(toLocalInput("")).toBe("");
    });

    it("does not turn unparseable input into a date", () => {
        expect(toLocalInput("not a date")).toBe("");
        expect(fromLocalInput("not a date")).toBeNull();
    });
});
