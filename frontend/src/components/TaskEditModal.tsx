import React, { useState, useRef, useEffect } from "react";
import { Modal, Button, Form } from "react-bootstrap";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api";
import { useEventTracker } from "../hooks/useEventTracker";
import CodeSolutionEditor from "./CodeSolutionEditor";
import {
    CONTEXT_STYLE,
    afterToPuzzle,
    beforeToPuzzle,
    puzzleToAfter,
    puzzleToBefore,
    type ContextParts,
} from "../lib/contextLines";
import { parseSplitPlan, type SplitEntry } from "../types/models";

interface TaskEditModalProps {
    /** The task being edited. Ignored when {@link create} is set. */
    taskId: string;
    /**
     * Turns the dialog into "add a tier": the same form, plus where the new tier goes, saved as
     * a new task of this sequence instead of over an existing one. There is no adjustment
     * rating — nothing was generated, so there is nothing to rate.
     */
    create?: { sequenceId: string; tierCount: number };
    initialProblemStatement: string;
    initialCode: string;
    /** True when no original has been saved yet — shows the rating input. */
    isFirstEdit: boolean;
    /** AP15: Current distractor lines (newline-separated). */
    initialDistractors?: string | null;
    /** AP15: JSON distractor plan from generation (read-only info). */
    distractorPlan?: string | null;
    /** AP31: Current inline-split plan (JSON string). Parsed with parseSplitPlan. */
    initialSplitPlan?: string | null;
    /** AP22: Current read-only code above / below the puzzle. */
    initialContextBefore?: string | null;
    initialContextAfter?: string | null;
    onSave: (updated: { statement: string; solution: string; distractors?: string | null; splitPlan?: string | null }) => void;
    onClose: () => void;
}

// Rating labels are now provided via i18n — see RATING_KEYS below
const RATING_KEYS: Record<number, string> = {
    1: "taskEdit.rating1",
    2: "taskEdit.rating2",
    3: "taskEdit.rating3",
    4: "taskEdit.rating4",
    5: "taskEdit.rating5",
};

const TaskEditModal: React.FC<TaskEditModalProps> = ({
    taskId,
    create,
    initialProblemStatement,
    initialCode,
    isFirstEdit: isFirstEditProp,
    initialDistractors,
    distractorPlan,
    initialSplitPlan,
    initialContextBefore,
    initialContextAfter,
    onSave,
    onClose,
}) => {
    const { t } = useTranslation();
    const isFirstEdit = isFirstEditProp && !create;
    // Where a new tier goes; defaults to the end. Tiers at or above it move up by one.
    const [position, setPosition] = useState<number>((create?.tierCount ?? 0) + 1);
    const [problemStatement, setProblemStatement] = useState(initialProblemStatement);
    const [code, setCode] = useState(initialCode);
    const [distractors, setDistractors] = useState(initialDistractors ?? "");
    // AP31: inline-split plan, seeded from the task's existing split_plan.
    const [splits, setSplits] = useState<SplitEntry[]>(() => parseSplitPlan(initialSplitPlan)?.splits ?? []);
    // AP22: context blocks around the puzzle; the switch starts on when the task has any.
    const [contextBefore, setContextBefore] = useState(initialContextBefore ?? "");
    const [contextAfter, setContextAfter] = useState(initialContextAfter ?? "");
    const [contextMode, setContextMode] = useState(Boolean(initialContextBefore || initialContextAfter));
    const [adjustmentRating, setAdjustmentRating] = useState<number>(3);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // AP14: Teacher edit tracking
    const { logEvent } = useEventTracker();
    const editStartRef = useRef(Date.now());
    const saveCountRef = useRef(0);

    useEffect(() => {
        editStartRef.current = Date.now();
        // A tier being added has no task id to log against yet; it is logged once it exists.
        if (!create) logEvent(taskId, "teacher_edit_started", { taskId });
    }, [taskId]);

    // AP22: move one line across a puzzle/context border; code and splits change together.
    const contextParts: ContextParts = { contextBefore, code, contextAfter, splits };
    const applyMove = (moved: ContextParts | null) => {
        if (!moved) return;
        setContextBefore(moved.contextBefore);
        setCode(moved.code);
        setContextAfter(moved.contextAfter);
        setSplits(moved.splits);
    };
    const moveButton = (move: (p: ContextParts) => ContextParts | null, icon: string, label: string) => (
        <Button
            variant="outline-secondary"
            size="sm"
            disabled={!move(contextParts)}
            onClick={() => applyMove(move(contextParts))}
        >
            <i className={`bi ${icon} me-1`}></i>
            {label}
        </Button>
    );

    const handleSave = async () => {
        if (!problemStatement.trim() || !code.trim()) {
            setError(t("taskEdit.emptyError"));
            return;
        }
        if (isFirstEdit && (adjustmentRating < 1 || adjustmentRating > 5)) {
            setError(t("taskEdit.ratingError"));
            return;
        }

        setSaving(true);
        setError(null);
        try {
            const body: Record<string, unknown> = {
                problemStatement: problemStatement,
                code: code,
                // AP31: authoritative split plan (empty array clears it server-side)
                splits: splits,
                // AP22: always sent, so switching context mode off clears it
                contextBefore: contextMode ? contextBefore : "",
                contextAfter: contextMode ? contextAfter : "",
            };
            // AP15: Include distractors in edit payload
            if (distractors !== (initialDistractors ?? "")) {
                body.distractors = distractors.trim() || null;
            }

            if (create) {
                body.tierLevel = position;
                const res = await api.post(`/sequence/${create.sequenceId}/tasks`, body);
                logEvent(String(res.data.id), "teacher_tier_added", {
                    taskId: res.data.id,
                    tierLevel: position,
                    editDurationMs: Date.now() - editStartRef.current,
                }, create.sequenceId, position);
                onSave({
                    statement: res.data.statement,
                    solution: res.data.solution,
                    distractors: res.data.distractors ?? null,
                    splitPlan: res.data.splitPlan ?? null,
                });
                return;
            }

            if (isFirstEdit) {
                body.adjustmentRating = adjustmentRating;
            }
            const res = await api.put(`/tasks/${taskId}`, body);
            saveCountRef.current += 1;
            const initialSplits = parseSplitPlan(initialSplitPlan)?.splits ?? [];
            const fieldsChanged: string[] = [];
            if (problemStatement !== initialProblemStatement) fieldsChanged.push("statement");
            if (code !== initialCode) fieldsChanged.push("code");
            if (distractors !== (initialDistractors ?? "")) fieldsChanged.push("distractors");
            if (JSON.stringify(splits) !== JSON.stringify(initialSplits)) fieldsChanged.push("splitPlan");
            if ((body.contextBefore as string) !== (initialContextBefore ?? "")
                || (body.contextAfter as string) !== (initialContextAfter ?? "")) {
                fieldsChanged.push("context");
            }
            logEvent(taskId, "teacher_edit_saved", {
                taskId,
                editDurationMs: Date.now() - editStartRef.current,
                fieldsChanged,
                saveCount: saveCountRef.current,
            });
            onSave({
                statement: res.data.statement,
                solution: res.data.solution,
                distractors: res.data.distractors ?? null,
                splitPlan: res.data.splitPlan ?? null,
            });
        } catch (err: unknown) {
            const msg =
                (err as { response?: { data?: { message?: string } } })?.response?.data
                    ?.message ?? t("taskEdit.errorSaving");
            setError(msg);
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal show onHide={() => {
            if (!create) {
                logEvent(taskId, "teacher_edit_cancelled", {
                    taskId,
                    editDurationMs: Date.now() - editStartRef.current,
                });
            }
            onClose();
        }} size="lg" centered>
            <Modal.Header closeButton>
                <Modal.Title>{create ? t("taskEdit.addTitle") : t("taskEdit.title")}</Modal.Title>
            </Modal.Header>
            <Modal.Body>
                {error && <div className="alert alert-danger">{error}</div>}

                {create && (
                    <Form.Group className="mb-3">
                        <Form.Label>{t("taskEdit.position")}</Form.Label>
                        <Form.Select
                            value={position}
                            onChange={(e) => setPosition(parseInt(e.target.value, 10))}
                        >
                            {Array.from({ length: create.tierCount + 1 }, (_, i) => i + 1).map((level) => (
                                <option key={level} value={level}>
                                    {level <= create.tierCount
                                        ? t("taskEdit.positionBefore", { level })
                                        : t("taskEdit.positionEnd", { level })}
                                </option>
                            ))}
                        </Form.Select>
                    </Form.Group>
                )}

                <Form.Group className="mb-3">
                    <Form.Label>{t("taskEdit.statement")}</Form.Label>
                    <Form.Control
                        as="textarea"
                        rows={6}
                        value={problemStatement}
                        onChange={(e) => setProblemStatement(e.target.value)}
                    />
                </Form.Group>

                {/* AP22: context mode — read-only code around the puzzle */}
                <Form.Check
                    type="switch"
                    id="taskEditContextMode"
                    className="mb-2"
                    label={t("taskEdit.contextMode")}
                    checked={contextMode}
                    onChange={(e) => setContextMode(e.target.checked)}
                />

                {contextMode && (
                    <Form.Group className="mb-2" controlId="taskEditContextBefore">
                        <Form.Label>
                            <i className="bi bi-lock me-1"></i>
                            {t("taskEdit.contextBefore")}
                        </Form.Label>
                        <Form.Control
                            as="textarea"
                            rows={3}
                            spellCheck={false}
                            style={CONTEXT_STYLE}
                            value={contextBefore}
                            onChange={(e) => setContextBefore(e.target.value)}
                        />
                        <div className="d-flex flex-wrap gap-2 mt-2">
                            {moveButton(beforeToPuzzle, "bi-arrow-down", t("taskEdit.beforeToPuzzle"))}
                            {moveButton(puzzleToBefore, "bi-arrow-up", t("taskEdit.puzzleToBefore"))}
                        </div>
                    </Form.Group>
                )}

                <Form.Group className="mb-3">
                    <Form.Label>{t("taskEdit.code")}</Form.Label>

                    {/* AP30 gaps + AP31 inline-split: shared with ManualTaskCreator */}
                    <CodeSolutionEditor
                        code={code}
                        onCodeChange={setCode}
                        splits={splits}
                        onSplitsChange={setSplits}
                        rows={10}
                        showGapPreview
                        onError={setError}
                    />

                    <Form.Text className="text-muted" style={{ fontSize: "0.8rem" }}>
                        <strong>{t("taskEdit.swapHelp")}</strong>{" "}
                        <code>#swap:NAME</code> — {t("taskEdit.swapSingle")}{" "}
                        <code>#group:NAME_N</code> — {t("taskEdit.swapGroup")}{" "}
                        {t("taskEdit.swapFixed")}
                    </Form.Text>
                </Form.Group>

                {contextMode && (
                    <Form.Group className="mb-3" controlId="taskEditContextAfter">
                        <div className="d-flex flex-wrap gap-2 mb-2">
                            {moveButton(puzzleToAfter, "bi-arrow-down", t("taskEdit.puzzleToAfter"))}
                            {moveButton(afterToPuzzle, "bi-arrow-up", t("taskEdit.afterToPuzzle"))}
                        </div>
                        <Form.Label>
                            <i className="bi bi-lock me-1"></i>
                            {t("taskEdit.contextAfter")}
                        </Form.Label>
                        <Form.Control
                            as="textarea"
                            rows={3}
                            spellCheck={false}
                            style={CONTEXT_STYLE}
                            value={contextAfter}
                            onChange={(e) => setContextAfter(e.target.value)}
                        />
                        <Form.Text className="text-muted" style={{ fontSize: "0.8rem" }}>
                            {t("taskEdit.contextHelp")}
                        </Form.Text>
                    </Form.Group>
                )}

                {/* AP15: Distractor editing */}
                <Form.Group className="mb-3">
                    <Form.Label>{t("taskEdit.distractors")}</Form.Label>
                    <Form.Control
                        as="textarea"
                        rows={4}
                        value={distractors}
                        onChange={(e) => setDistractors(e.target.value)}
                        placeholder={t("taskEdit.distractorsPlaceholder")}
                        style={{ fontFamily: "monospace", fontSize: "0.9rem" }}
                    />
                    <Form.Text className="text-muted" style={{ fontSize: "0.8rem" }}>
                        {t("taskEdit.distractorsHelp")}
                    </Form.Text>
                    {distractorPlan && (
                        <div className="mt-1">
                            <small className="text-muted">
                                <strong>{t("taskEdit.distractorPlan")}:</strong>{" "}
                                {(() => {
                                    try {
                                        const plan = JSON.parse(distractorPlan);
                                        return `${plan.type} — ${plan.rationale}`;
                                    } catch {
                                        return distractorPlan;
                                    }
                                })()}
                            </small>
                        </div>
                    )}
                </Form.Group>

                {isFirstEdit && (
                    <Form.Group className="mb-3">
                        <Form.Label>
                            {t("taskEdit.ratingQuestion")}
                        </Form.Label>
                        <div className="d-flex align-items-center gap-2">
                            <Form.Range
                                min={1}
                                max={5}
                                step={1}
                                value={adjustmentRating}
                                onChange={(e) =>
                                    setAdjustmentRating(parseInt(e.target.value, 10))
                                }
                            />
                            <span className="badge bg-secondary">
                                {adjustmentRating} &ndash; {t(RATING_KEYS[adjustmentRating])}
                            </span>
                        </div>
                    </Form.Group>
                )}
            </Modal.Body>
            <Modal.Footer>
                <Button variant="secondary" onClick={onClose} disabled={saving}>
                    {t("taskEdit.cancel")}
                </Button>
                <Button variant="primary" onClick={handleSave} disabled={saving}>
                    {saving ? t("taskEdit.saving") : t("taskEdit.save")}
                </Button>
            </Modal.Footer>
        </Modal>
    );
};

export default TaskEditModal;
