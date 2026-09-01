import { describe, it, expect } from "vitest";
import { apiErrorMessage } from "../../src/lib/api.ts";

/**
 * The backend's GlobalExceptionHandler always replies with an ApiErrorResponse
 * object ({ error, message }). Callers used to put that object straight into
 * component state and render it, which throws "Objects are not valid as a React
 * child" and — with no error boundary at the time — blanked the whole app.
 *
 * The contract these tests pin down is narrow but load-bearing: whatever comes
 * back, the result must be a non-empty string that is safe to render.
 */
describe("apiErrorMessage", () => {
    it("combines error and message from the real ApiErrorResponse shape", () => {
        const err = {
            response: {
                data: {
                    error: "Ungültige Eingabe",
                    message: "Worksheet name must not be empty.",
                },
            },
        };
        expect(apiErrorMessage(err)).toBe("Ungültige Eingabe: Worksheet name must not be empty.");
    });

    it("falls back to message alone when there is no error title", () => {
        expect(apiErrorMessage({ response: { data: { message: "boom" } } })).toBe("boom");
    });

    it("falls back to the error title alone when there is no message", () => {
        expect(apiErrorMessage({ response: { data: { error: "Zugriff verweigert" } } }))
            .toBe("Zugriff verweigert");
    });

    it("passes a plain string body through unchanged", () => {
        expect(apiErrorMessage({ response: { data: "already a string" } }))
            .toBe("already a string");
    });

    it("uses the fallback for a blank string body", () => {
        // A 401 from ResponseEntity.build() has an empty body; `??` would keep
        // the empty string and the user would see nothing at all.
        expect(apiErrorMessage({ response: { data: "   " } }, "fallback")).toBe("fallback");
    });

    it("uses the axios message when the request never reached the server", () => {
        expect(apiErrorMessage({ message: "Network Error" })).toBe("Network Error");
    });

    it("uses the fallback when there is nothing to go on", () => {
        expect(apiErrorMessage({}, "fallback")).toBe("fallback");
        expect(apiErrorMessage(undefined, "fallback")).toBe("fallback");
    });

    it("never returns a non-string, whatever the body looks like", () => {
        const bodies = [
            null,
            undefined,
            0,
            [],
            { unexpected: "shape" },
            { error: null, message: null },
        ];
        for (const data of bodies) {
            const result = apiErrorMessage({ response: { data } }, "fallback");
            expect(typeof result).toBe("string");
            expect(result.length).toBeGreaterThan(0);
        }
    });
});
