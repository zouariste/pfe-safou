import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { beforeAll, describe, expect, it } from "vitest";

/**
 * Runs the shipped js-parsons grader, not a copy of its logic. The widget's DOM is reduced to the
 * one thing grading reads from it: the order of the blocks in the solution list.
 */
type Line = { id: string; indent: number };
type Widget = {
    init(text: string): void;
    modified_lines: Line[];
    model_solution: Line[];
};
type WidgetClass = {
    new (options: Record<string, unknown>): Widget;
    _graders: { LineBasedGrader: new (widget: Widget) => { grade(): { success: boolean; errors: string[] } } };
};

const SORTABLE_ID = "target";

beforeAll(() => {
    const dir = resolve(__dirname, "../../public/jsparsons");
    for (const file of ["lib/jquery.min.js", "lib/underscore-min.js", "lib/lis.js", "parsons.js"]) {
        // Indirect eval: these are classic scripts that expect to define browser globals.
        (0, eval)(readFileSync(resolve(dir, file), "utf-8"));
    }
    const jq = (globalThis as unknown as { $: { fn: Record<string, unknown> } }).$;
    jq.fn.sortable = function (this: { children(): { toArray(): HTMLElement[] } }) {
        return this.children().toArray().map((li) => li.id);
    };
});

/**
 * Grades an attempt.
 *
 * @param solution the task's solution, with its markers
 * @param order    the solution's line numbers (from 0) in the order the student placed them, each
 *                 block at its correct indentation
 */
function grade(solution: string[], order: number[]) {
    document.body.innerHTML = `<ul id="ul-${SORTABLE_ID}"></ul>`;
    const { ParsonsWidget } = globalThis as unknown as { ParsonsWidget: WidgetClass };
    const widget = new ParsonsWidget({ sortableId: SORTABLE_ID, max_wrong_lines: 0 });
    widget.init(solution.join("\n"));

    const list = document.getElementById(`ul-${SORTABLE_ID}`)!;
    for (const lineNumber of order) {
        const block = widget.modified_lines[lineNumber];
        block.indent = widget.model_solution[lineNumber].indent;
        const li = document.createElement("li");
        li.id = block.id;
        list.appendChild(li);
    }
    return new ParsonsWidget._graders.LineBasedGrader(widget).grade();
}

describe("LineBasedGrader with #swap lines", () => {
    const solution = ["a = 1 #swap:init", "b = 2 #swap:init", "print('x')", "c = 3 #swap:later", "d = 4 #swap:later"];

    it("accepts lines with the same name in any order", () => {
        expect(grade(solution, [1, 0, 2, 4, 3]).success).toBe(true);
    });

    it("does not swap lines with different names", () => {
        expect(grade(solution, [3, 1, 2, 0, 4]).success).toBe(false);
    });

    it("keeps unmarked lines position-fixed", () => {
        expect(grade(solution, [2, 0, 1, 3, 4]).success).toBe(false);
    });
});

describe("LineBasedGrader with #group clusters", () => {
    const distinct = [
        "def a(): #group:f_1",
        "    return 1 #group:f_1",
        "def b(): #group:f_2",
        "    return 2 #group:f_2",
        "    # done #group:f_2",
    ];

    it("accepts the solution order", () => {
        expect(grade(distinct, [0, 1, 2, 3, 4]).success).toBe(true);
    });

    it("accepts clusters with a shared prefix in any order, even of different sizes", () => {
        expect(grade(distinct, [2, 3, 4, 0, 1]).success).toBe(true);
    });

    it("rejects a cluster that was split up", () => {
        expect(grade(distinct, [0, 2, 1, 3, 4]).success).toBe(false);
    });

    it("rejects a cluster whose lines were reordered", () => {
        expect(grade(distinct, [1, 0, 2, 3, 4]).success).toBe(false);
    });

    describe("with a line occurring in both clusters", () => {
        const shared = [
            "def a(): #group:f_1",
            "    x = 1 #group:f_1",
            "    return x #group:f_1",
            "def b(): #group:f_2",
            "    x = 2 #group:f_2",
            "    return x #group:f_2",
        ];

        it("accepts the solution order", () => {
            expect(grade(shared, [0, 1, 2, 3, 4, 5]).success).toBe(true);
        });

        it("accepts the swapped clusters", () => {
            expect(grade(shared, [3, 4, 5, 0, 1, 2]).success).toBe(true);
        });

        it("accepts the shared line taken from the other cluster, as the blocks look identical", () => {
            // A student cannot tell the two `return x` blocks apart.
            expect(grade(shared, [3, 4, 2, 0, 1, 5]).success).toBe(true);
        });

        it("still rejects a misplaced shared line", () => {
            expect(grade(shared, [0, 2, 1, 3, 4, 5]).success).toBe(false);
        });
    });

    it("checks a shared line's indentation against its own cluster", () => {
        const nested = [
            "def a(): #group:f_1",
            "    done() #group:f_1",
            "if b: #group:f_2",
            "    if c: #group:f_2",
            "        done() #group:f_2",
        ];
        expect(grade(nested, [2, 3, 4, 0, 1]).success).toBe(true);
    });

    it("swaps clusters of the same length around a fixed line", () => {
        const solution = ["a = 1 #group:f_1", "b = 2 #group:f_1", "print('x')", "c = 3 #group:f_2", "d = 4 #group:f_2"];
        expect(grade(solution, [3, 4, 2, 0, 1]).success).toBe(true);
    });

    it("does not swap clusters of different lengths around a fixed line", () => {
        // A known limit the help page mentions: the slots they would take straddle the fixed line.
        const solution = ["a = 1 #group:f_1", "b = 2 #group:f_1", "print('x')", "c = 3 #group:f_2"];
        expect(grade(solution, [3, 2, 0, 1]).success).toBe(false);
    });

    it("keeps differently named groups position-fixed", () => {
        // Deliberate: only a shared prefix makes clusters interchangeable, and the editor warns
        // about names without a partner (findLoneGroups).
        const named = [
            "def a(): #group:einlesen",
            "    return 1 #group:einlesen",
            "def b(): #group:jahr",
            "    return 2 #group:jahr",
        ];
        expect(grade(named, [0, 1, 2, 3]).success).toBe(true);
        expect(grade(named, [2, 3, 0, 1]).success).toBe(false);
    });
});
