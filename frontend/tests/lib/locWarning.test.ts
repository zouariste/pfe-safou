import { describe, expect, it } from "vitest";
import {
    describeOverLongTasks,
    generationOutcome,
    locWarningFromState,
    MAX_GENERATION_ATTEMPTS,
} from "../../src/lib/locWarning";
import type { LocWarning } from "../../src/types/models";

const warning: LocWarning = {
    maxLoc: 20,
    tasks: [
        { tierLevel: 2, linesOfCode: 27 },
        { tierLevel: 3, linesOfCode: 31 },
    ],
};

describe("generationOutcome", () => {
    it("opens a sequence without a warning", () => {
        expect(generationOutcome(undefined, 1)).toBe("open");
        expect(generationOutcome(null, 1)).toBe("open");
        expect(generationOutcome({ maxLoc: 20, tasks: [] }, 1)).toBe("open");
    });

    it("offers another try while attempts are left", () => {
        expect(generationOutcome(warning, 1)).toBe("offerRetry");
        expect(generationOutcome(warning, MAX_GENERATION_ATTEMPTS - 1)).toBe("offerRetry");
    });

    it("opens the sequence once the attempts are used up", () => {
        expect(generationOutcome(warning, MAX_GENERATION_ATTEMPTS)).toBe("open");
    });
});

describe("describeOverLongTasks", () => {
    it("lists each task with its tier and lines", () => {
        expect(describeOverLongTasks(warning, (tier, lines) => `T${tier} (${lines})`)).toBe("T2 (27), T3 (31)");
    });
});

describe("locWarningFromState", () => {
    it("reads a warning out of router state", () => {
        expect(locWarningFromState({ locWarning: warning })).toEqual(warning);
    });

    it("ignores state without a usable warning", () => {
        expect(locWarningFromState(null)).toBeNull();
        expect(locWarningFromState({ fromWorksheet: "x" })).toBeNull();
        expect(locWarningFromState({ locWarning: { maxLoc: 20, tasks: [] } })).toBeNull();
        expect(locWarningFromState({ locWarning: "nonsense" })).toBeNull();
    });
});
