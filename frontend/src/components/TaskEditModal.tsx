import React, { useState, useRef, useEffect } from "react";
import { Modal, Button, Form } from "react-bootstrap";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api";
import { useEventTracker } from "../hooks/useEventTracker";
import { makeGapMarker, renderGapLine, hasGapMarker, type GapMode } from "../lib/gapMarkers";

interface TaskEditModalProps {
    taskId: string;
    initialProblemStatement: string;
    initialCode: string;
    /** True when no original has been saved yet — shows the rating input. */
    isFirstEdit: boolean;
    /** AP15: Current distractor lines (newline-separated). */
    initialDistractors?: string | null;
    /** AP15: JSON distractor plan from generation (read-only info). */
    distractorPlan?: string | null;
    onSave: (updated: { statement: string; solution: string; distractors?: string | null }) => void;
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
    initialProblemStatement,
    initialCode,
    isFirstEdit,
    initialDistractors,
    distractorPlan,
    onSave,
    onClose,
}) => {
    const { t } = useTranslation();
    const [problemStatement, setProblemStatement] = useState(initialProblemStatement);
    const [code, setCode] = useState(initialCode);
    const [distractors, setDistractors] = useState(initialDistractors ?? "");
    const [adjustmentRating, setAdjustmentRating] = useState<number>(3);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // AP30: fill-in-the-gaps tooling for the code field (mirrors ManualTaskCreator)
    const codeTaRef = useRef<HTMLTextAreaElement>(null);
    const [gapMode, setGapMode] = useState<GapMode>("trim");

    const markSelectionAsGap = () => {
        const ta = codeTaRef.current;
        if (!ta) return;
        const start = ta.selectionStart;
        const end = ta.selectionEnd;
        if (start == null || end == null || start === end) {
            setError(t("taskEdit.gapSelectHint", "Bitte zuerst Text innerhalb einer Code-Zeile markieren, um eine Lücke zu setzen."));
            return;
        }
        const selected = code.substring(start, end);
        if (selected.includes("\n")) {
            setError(t("taskEdit.gapSingleLine", "Eine Lücke darf nur innerhalb einer einzelnen Zeile liegen."));
            return;
        }
        const marker = makeGapMarker(selected, gapMode);
        const newVal = code.substring(0, start) + marker + code.substring(end);
        setCode(newVal);
        setError(null);
        requestAnimationFrame(() => {
            ta.focus();
            const pos = start + marker.length;
            ta.selectionStart = ta.selectionEnd = pos;
        });
    };

    // AP14: Teacher edit tracking
    const { logEvent } = useEventTracker();
    const editStartRef = useRef(Date.now());
    const saveCountRef = useRef(0);

    useEffect(() => {
        editStartRef.current = Date.now();
        logEvent(taskId, "teacher_edit_started", { taskId });
    }, [taskId]);

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
            };
            // AP15: Include distractors in edit payload
            if (distractors !== (initialDistractors ?? "")) {
                body.distractors = distractors.trim() || null;
            }
            if (isFirstEdit) {
                body.adjustmentRating = adjustmentRating;
            }
            const res = await api.put(`/tasks/${taskId}`, body);
            saveCountRef.current += 1;
            const fieldsChanged: string[] = [];
            if (problemStatement !== initialProblemStatement) fieldsChanged.push("statement");
            if (code !== initialCode) fieldsChanged.push("code");
            if (distractors !== (initialDistractors ?? "")) fieldsChanged.push("distractors");
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
            logEvent(taskId, "teacher_edit_cancelled", {
                taskId,
                editDurationMs: Date.now() - editStartRef.current,
            });
            onClose();
        }} size="lg" centered>
            <Modal.Header closeButton>
                <Modal.Title>{t("taskEdit.title")}</Modal.Title>
            </Modal.Header>
            <Modal.Body>
                {error && <div className="alert alert-danger">{error}</div>}

                <Form.Group className="mb-3">
                    <Form.Label>{t("taskEdit.statement")}</Form.Label>
                    <Form.Control
                        as="textarea"
                        rows={6}
                        value={problemStatement}
                        onChange={(e) => setProblemStatement(e.target.value)}
                    />
                </Form.Group>

                <Form.Group className="mb-3">
                    <Form.Label>{t("taskEdit.code")}</Form.Label>

                    {/* AP30: Fill-in-the-Gaps toolbar */}
                    <div className="d-flex align-items-center gap-2 mb-2 flex-wrap">
                        <button
                            type="button"
                            className="btn btn-sm btn-outline-warning"
                            onClick={markSelectionAsGap}
                            title={t("taskEdit.gapButtonTitle", "Markierten Text in der Code-Zeile als Freitext-Lücke setzen")}
                        >
                            <i className="bi bi-input-cursor-text me-1"></i>
                            {t("taskEdit.gapButton", "Auswahl als Lücke")}
                        </button>
                        <select
                            className="form-select form-select-sm"
                            style={{ width: "auto" }}
                            value={gapMode}
                            onChange={(e) => setGapMode(e.target.value as GapMode)}
                            title={t("taskEdit.gapModeTitle", "Vergleichsmodus für neue Lücken")}
                        >
                            <option value="trim">trim (Standard)</option>
                            <option value="exact">exact</option>
                            <option value="ci">case-insensitive</option>
                        </select>
                        <small className="text-muted">
                            {t("taskEdit.gapHint", "Text markieren → „Auswahl als Lücke“. Mehrere Lücken pro Zeile möglich.")}
                        </small>
                    </div>

                    <Form.Control
                        ref={codeTaRef}
                        as="textarea"
                        rows={10}
                        value={code}
                        onChange={(e) => setCode(e.target.value)}
                        style={{ fontFamily: "monospace", fontSize: "0.9rem" }}
                    />
                    <Form.Text className="text-muted" style={{ fontSize: "0.8rem" }}>
                        <strong>{t("taskEdit.swapHelp")}</strong>{" "}
                        <code>#swap:NAME</code> — {t("taskEdit.swapSingle")}{" "}
                        <code>#group:NAME_N</code> — {t("taskEdit.swapGroup")}{" "}
                        {t("taskEdit.swapFixed")}
                    </Form.Text>

                    {/* AP30: gap preview — lines containing $$blank$$ markers, rendered as chips */}
                    {hasGapMarker(code) && (
                        <div className="mt-2 p-2 rounded border bg-light">
                            <div className="small text-muted mb-1">
                                <i className="bi bi-eye me-1"></i>
                                {t("taskEdit.gapPreview", "Lücken-Vorschau")}
                            </div>
                            <div className="font-monospace small">
                                {code.split("\n").filter((l) => hasGapMarker(l)).map((l, i) => (
                                    <div key={i} style={{ whiteSpace: "pre-wrap" }}>{renderGapLine(l)}</div>
                                ))}
                            </div>
                        </div>
                    )}
                </Form.Group>

                {/* AP15: Distractor editing */}
                <Form.Group className="mb-3">
                    <Form.Label>{t("taskEdit.distractors", "Distractors")}</Form.Label>
                    <Form.Control
                        as="textarea"
                        rows={4}
                        value={distractors}
                        onChange={(e) => setDistractors(e.target.value)}
                        placeholder={t("taskEdit.distractorsPlaceholder", "One distractor per line, each ending with #distractor")}
                        style={{ fontFamily: "monospace", fontSize: "0.9rem" }}
                    />
                    <Form.Text className="text-muted" style={{ fontSize: "0.8rem" }}>
                        {t("taskEdit.distractorsHelp", "Each line must end with #distractor. Do not use #swap or #group on distractor lines.")}
                    </Form.Text>
                    {distractorPlan && (
                        <div className="mt-1">
                            <small className="text-muted">
                                <strong>{t("taskEdit.distractorPlan", "Plan")}:</strong>{" "}
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
