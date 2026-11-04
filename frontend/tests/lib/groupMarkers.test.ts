import { describe, it, expect } from "vitest";
import { findLoneGroups } from "../../src/lib/groupMarkers.ts";

describe("findLoneGroups", () => {
    it("reports differently named groups, which cannot swap with each other", () => {
        const code = [
            "def a(): #group:einlesen",
            "    return 1 #group:einlesen",
            "def b(): #group:jahr",
            "def c(): #group:nachricht",
        ].join("\n");
        expect(findLoneGroups(code)).toEqual(["einlesen", "jahr", "nachricht"]);
    });

    it("accepts clusters that share a prefix", () => {
        expect(findLoneGroups("a = 1 #group:f_1\nb = 2 #group:f_2")).toEqual([]);
    });

    it("reports a numbered cluster without a partner", () => {
        expect(findLoneGroups("a = 1 #group:f_1\nb = 2 #group:f_1")).toEqual(["f_1"]);
    });

    it("judges each prefix on its own", () => {
        expect(findLoneGroups("a #group:f_1\nb #group:f_2\nc #group:solo")).toEqual(["solo"]);
    });

    it("ignores code without group markers", () => {
        expect(findLoneGroups("")).toEqual([]);
        expect(findLoneGroups("a = 1\nb = 2 #swap:init\nc = 3 #distractor")).toEqual([]);
    });
});
