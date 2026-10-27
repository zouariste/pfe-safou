import type { SplitEntry } from "./models.ts";

/*
 * The parts of the vendored js-parsons library (public/jsparsons/parsons.js) that SolvePage
 * reads, writes or wraps. The library is plain JavaScript without typings, so this covers only
 * what the page uses, including the fields our own patches to the library and SolvePage add.
 */

/** An AP30 fill-in-the-gap input inside a code line. */
export interface ParsonsBlank {
    el: HTMLInputElement;
    /** Null when the gap is not auto-graded. */
    expected: string | null;
    mode?: string;
}

/** A fragment sitting in a slot of an AP31 split line. */
export interface ParsonsSplitFragment {
    text: string;
    distractor: boolean;
}

/** The state of an AP31 split line, set up by `_renderSplit`. */
export interface ParsonsSplitState {
    canonical: string[];
    currentOrder(): (ParsonsSplitFragment | null)[];
}

/** One draggable block (ParsonsCodeline in parsons.js). */
export interface ParsonsCodeLine {
    id: string;
    code: string;
    indent: number;
    elem?(): JQuery<HTMLElement>;
    _blanks?: ParsonsBlank[];
    _split?: ParsonsSplitState;
    isSplitLine?(): boolean;
    splitCorrect(): boolean;
    _renderSplit?(entry: SplitEntry, onChange: () => void): void;
    /** Not from the library: SolvePage's grading records its verdict here. */
    isCorrect?: boolean;
}

/** What parsons.js passes to addLogEntry; the move actions carry the moved block's id. */
export interface ParsonsLogEntry {
    type: string;
    target?: string;
}

export interface ParsonsGrader {
    grade(elementId?: string): unknown;
}

/** parsons.js calls the grader with `new`, so it fills in `this` rather than returning. */
export type ParsonsGraderFactory = (this: ParsonsGrader, widget: ParsonsWidget) => void;

export interface ParsonsOptions {
    sortableId: string;
    trashId?: string;
    can_indent?: boolean;
    max_wrong_lines?: number;
    programmingLang?: string;
    trash_label?: string;
    solution_label?: string;
    x_indent?: number;
    grader?: ParsonsGraderFactory;
}

export interface ParsonsWidget {
    options: ParsonsOptions;
    /** Message builders the graders write into the feedback box, keyed by message id. */
    translations?: Record<string, (...args: never[]) => string>;
    modified_lines?: ParsonsCodeLine[];
    extra_lines?: ParsonsCodeLine[];
    _clickToMoveState?: object | null;
    /** Not from the library: set by SolvePage's getFeedback wrapper. */
    _lastSuccess?: boolean;
    init(code: string): void;
    shuffleLines(): void;
    /** For the line-based grader, an array of messages. */
    getFeedback(): unknown;
    addLogEntry(entry: ParsonsLogEntry, isMove?: boolean): unknown;
    getLineById?(id: string): ParsonsCodeLine | undefined;
    enableClickToMove(): void;
    disableClickToMove(): void;
}

export interface ParsonsWidgetConstructor {
    new (options: ParsonsOptions): ParsonsWidget;
    _graders: { LineBasedGrader: new (widget: ParsonsWidget) => ParsonsGrader };
}
