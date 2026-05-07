import React, { useState, useRef, useEffect } from "react";
import { Modal, Button, Form } from "react-bootstrap";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api";
import { useEventTracker } from "../hooks/useEventTracker";

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
                    <Form.Control
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
