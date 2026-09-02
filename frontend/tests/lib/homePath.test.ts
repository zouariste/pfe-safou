import { describe, it, expect } from "vitest";
import { homePathFor, loginLandingPath } from "../../src/lib/homePath.ts";

/**
 * The classes page is where the normal workflow starts for a registered user,
 * so Home on a worksheet, sequence or task returns there rather than to the
 * sequence generator. The two roles have separate pages, matching the navbar.
 */
describe("homePathFor", () => {
    it("sends a student to their own classes page", () => {
        expect(homePathFor({ isLoggedIn: true, isStudent: true })).toBe("/my-classes");
    });

    it("sends a teacher or admin to class management", () => {
        expect(homePathFor({ isLoggedIn: true, isStudent: false })).toBe("/classes");
    });

    it("keeps anonymous visitors on the public home page", () => {
        // Sequences, worksheets and tasks are viewable via a shared link without
        // an account. Both class pages sit behind ProtectedRoute, so sending an
        // anonymous visitor there would bounce them to a login screen.
        expect(homePathFor({ isLoggedIn: false, isStudent: false })).toBe("/");
        expect(homePathFor({ isLoggedIn: false, isStudent: true })).toBe("/");
    });

    it("never routes anyone to a page they cannot open", () => {
        const cases = [
            { isLoggedIn: true, isStudent: true },
            { isLoggedIn: true, isStudent: false },
            { isLoggedIn: false, isStudent: true },
            { isLoggedIn: false, isStudent: false },
        ];
        for (const auth of cases) {
            const path = homePathFor(auth);
            if (!auth.isLoggedIn) expect(path).toBe("/");
            else expect(["/classes", "/my-classes"]).toContain(path);
        }
    });
});

describe("loginLandingPath", () => {
    it("lands a student on their classes list", () => {
        expect(loginLandingPath("STUDENT")).toBe("/my-classes");
    });

    it("lands teachers and admins on class management", () => {
        expect(loginLandingPath("TEACHER")).toBe("/classes");
        expect(loginLandingPath("ADMIN")).toBe("/classes");
    });

    it("does not depend on how many classes the user is in", () => {
        // Students used to be dropped straight into a class when they had
        // exactly one, so the landing page varied with class count.
        expect(loginLandingPath("STUDENT")).toBe(loginLandingPath("STUDENT"));
    });

    it("falls back to class management for an unknown or missing role", () => {
        expect(loginLandingPath(null)).toBe("/classes");
        expect(loginLandingPath(undefined)).toBe("/classes");
        expect(loginLandingPath("")).toBe("/classes");
    });
});
