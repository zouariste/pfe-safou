import { describe, it, expect } from "vitest";
import { findLoneGroups, findSplitGroups } from "../../src/lib/groupMarkers.ts";

describe("findSplitGroups", () => {
    it("reports a cluster with another line between its lines", () => {
        expect(findSplitGroups("a #group:f_1\nprint('x')\nb #group:f_1\nc #group:f_2")).toEqual(["f_1"]);
    });

    it("reports clusters that are interleaved with each other", () => {
        expect(findSplitGroups("a #group:f_1\nb #group:f_2\nc #group:f_1\nd #group:f_2")).toEqual(["f_1", "f_2"]);
    });

    it("accepts clusters on consecutive lines", () => {
        expect(findSplitGroups("a #group:f_1\nb #group:f_1\nprint('x')\nc #group:f_2")).toEqual([]);
    });

    it("does not count lines that never become part of the solution", () => {
        expect(findSplitGroups("a #group:f_1\n\nb #group:f_1\nc #group:f_2")).toEqual([]);
        expect(findSplitGroups("a #group:f_1\nwrong = 1 #distractor\nb #group:f_1\nc #group:f_2")).toEqual([]);
    });

    it("leaves a lone group to findLoneGroups", () => {
        const code = "a #group:solo\nprint('x')\nb #group:solo";
        expect(findSplitGroups(code)).toEqual([]);
        expect(findLoneGroups(code)).toEqual(["solo"]);
    });
});

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
