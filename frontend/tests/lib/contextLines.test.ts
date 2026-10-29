import { describe, expect, it } from "vitest";
import {
    afterToPuzzle,
    beforeToPuzzle,
    normalizeContext,
    puzzleToAfter,
    puzzleToBefore,
    type ContextParts,
} from "../../src/lib/contextLines";
import type { SplitEntry } from "../../src/types/models";

const split = (lineIndex: number): SplitEntry => ({ lineIndex, fragments: ["a", "b"] });

const parts = (over: Partial<ContextParts> = {}): ContextParts => ({
    contextBefore: "public class Sum {",
    code: "    int sum(int a, int b) {\n        return a + b;\n    }",
    contextAfter: "}",
    splits: [],
    ...over,
});

describe("normalizeContext", () => {
    it("drops surrounding blank lines but keeps indentation", () => {
        expect(normalizeContext("\n  \n    }\n}\n\n")).toBe("    }\n}");
    });

    it("turns blank or missing context into null", () => {
        expect(normalizeContext(" \n\t")).toBeNull();
        expect(normalizeContext(undefined)).toBeNull();
    });
});

describe("puzzleToBefore", () => {
    it("moves the first puzzle line to the end of the context", () => {
        const moved = puzzleToBefore(parts())!;

        expect(moved.contextBefore).toBe("public class Sum {\n    int sum(int a, int b) {");
        expect(moved.code).toBe("        return a + b;\n    }");
    });

    it("skips leading blank lines and shifts the splits by every removed line", () => {
        const moved = puzzleToBefore(parts({ code: "\nfirst\nsecond\nthird", splits: [split(1), split(3)] }))!;

        expect(moved.code).toBe("second\nthird");
        // the split of the moved line is dropped, the other follows its line
        expect(moved.splits).toEqual([split(1)]);
    });

    it("strips swap/group markers and gap markers, which context would show verbatim", () => {
        const moved = puzzleToBefore(parts({ contextBefore: "", code: "int x = $$blank:5|exact$$; #swap:init\nreturn x;" }))!;

        expect(moved.contextBefore).toBe("int x = 5;");
    });

    it("refuses to move the last puzzle line", () => {
        expect(puzzleToBefore(parts({ code: "only();\n\n" }))).toBeNull();
    });
});

describe("beforeToPuzzle", () => {
    it("moves the last context line to the start of the puzzle and shifts the splits", () => {
        const moved = beforeToPuzzle(parts({ contextBefore: "import x;\nclass A {\n", splits: [split(0)] }))!;

        expect(moved.contextBefore).toBe("import x;");
        expect(moved.code.split("\n")[0]).toBe("class A {");
        expect(moved.splits).toEqual([split(1)]);
    });

    it("is not possible without context", () => {
        expect(beforeToPuzzle(parts({ contextBefore: "  \n" }))).toBeNull();
    });
});

describe("puzzleToAfter", () => {
    it("moves the last puzzle line to the start of the context and drops its split", () => {
        const moved = puzzleToAfter(parts({ code: "a();\nb();\n\n", splits: [split(0), split(1)] }))!;

        expect(moved.code).toBe("a();");
        expect(moved.contextAfter).toBe("b();\n}");
        expect(moved.splits).toEqual([split(0)]);
    });

    it("refuses to move the last puzzle line", () => {
        expect(puzzleToAfter(parts({ code: "only();" }))).toBeNull();
    });
});

describe("afterToPuzzle", () => {
    it("moves the first context line to the end of the puzzle, after any trailing blank lines are gone", () => {
        const moved = afterToPuzzle(parts({ code: "a();  \n\n", contextAfter: "\n    }\n}", splits: [split(0)] }))!;

        expect(moved.code).toBe("a();  \n    }");
        expect(moved.contextAfter).toBe("}");
        expect(moved.splits).toEqual([split(0)]);
    });

    it("is not possible without context", () => {
        expect(afterToPuzzle(parts({ contextAfter: "" }))).toBeNull();
    });
});

describe("round trips", () => {
    it("moving a line out and back restores the puzzle and context", () => {
        const start = parts();
        expect(beforeToPuzzle(puzzleToBefore(start)!)).toEqual(start);
        expect(afterToPuzzle(puzzleToAfter(start)!)).toEqual(start);
    });
});
