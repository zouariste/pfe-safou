import { useState } from "react";
import { useTranslation } from "react-i18next";
import { api, apiErrorMessage } from "../lib/api.ts";
import type { ClassWorksheetInfo } from "../types/models.ts";
import { toLocalInput, fromLocalInput } from "../lib/scheduleTime.ts";

interface Props {
    classId: string;
    worksheet: ClassWorksheetInfo;
    onSaved: (patch: Partial<ClassWorksheetInfo>) => void;
}

/**
 * Release date, deadline and grace period for one worksheet in one class.
 *
 * Every field is optional, and empty means unconstrained: no release makes it
 * available at once, no deadline leaves it open indefinitely, no grace makes the
 * deadline sharp. The badge shows the state the server resolved, so it cannot
 * disagree with the server about what time it is.
 */
export default function WorksheetSchedule({ classId, worksheet, onSaved }: Props) {
    const { t } = useTranslation();
    const [open, setOpen] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const [releaseAt, setReleaseAt] = useState(toLocalInput(worksheet.releaseAt));
    const [deadlineAt, setDeadlineAt] = useState(toLocalInput(worksheet.deadlineAt));
    const [grace, setGrace] = useState(
        worksheet.graceMinutes != null ? String(worksheet.graceMinutes) : "",
    );

    const badge = () => {
        switch (worksheet.availability) {
            case "SCHEDULED":
                return <span className="badge text-bg-secondary">{t("schedule.scheduled")}</span>;
            case "CLOSED":
                return <span className="badge text-bg-danger">{t("schedule.closed")}</span>;
            case "OPEN":
            default:
                return <span className="badge text-bg-success">{t("schedule.open")}</span>;
        }
    };

    async function save() {
        setSaving(true);
        setError(null);

        const graceValue = grace.trim() === "" ? null : Number(grace);
        if (graceValue !== null && (!Number.isInteger(graceValue) || graceValue < 0)) {
            setError(t("schedule.graceInvalid"));
            setSaving(false);
            return;
        }

        const payload = {
            releaseAt: fromLocalInput(releaseAt),
            deadlineAt: fromLocalInput(deadlineAt),
            graceMinutes: graceValue,
        };

        try {
            await api.put(`/classes/${classId}/worksheets/${worksheet.worksheetId}/schedule`, payload);
            onSaved(payload);
            setOpen(false);
        } catch (err) {
            setError(apiErrorMessage(err, t("schedule.saveFailed")));
        } finally {
            setSaving(false);
        }
    }

    return (
        <div className="mt-2">
            <div className="d-flex align-items-center gap-2">
                {badge()}
                <button
                    className="btn btn-sm btn-link p-0"
                    onClick={() => setOpen(!open)}
                    aria-expanded={open}
                >
                    {open ? t("schedule.hide") : t("schedule.edit")}
                </button>
            </div>

            {open && (
                <div className="card card-body mt-2 py-2">
                    <div className="row g-2 align-items-end">
                        <div className="col-sm-4">
                            <label className="form-label mb-1 small" htmlFor={`rel-${worksheet.worksheetId}`}>
                                {t("schedule.releaseAt")}
                            </label>
                            <input
                                id={`rel-${worksheet.worksheetId}`}
                                type="datetime-local"
                                className="form-control form-control-sm"
                                value={releaseAt}
                                onChange={(e) => setReleaseAt(e.target.value)}
                            />
                            <small className="text-muted">{t("schedule.releaseHint")}</small>
                        </div>
                        <div className="col-sm-4">
                            <label className="form-label mb-1 small" htmlFor={`dl-${worksheet.worksheetId}`}>
                                {t("schedule.deadlineAt")}
                            </label>
                            <input
                                id={`dl-${worksheet.worksheetId}`}
                                type="datetime-local"
                                className="form-control form-control-sm"
                                value={deadlineAt}
                                onChange={(e) => setDeadlineAt(e.target.value)}
                            />
                            <small className="text-muted">{t("schedule.deadlineHint")}</small>
                        </div>
                        <div className="col-sm-4">
                            <label className="form-label mb-1 small" htmlFor={`gr-${worksheet.worksheetId}`}>
                                {t("schedule.graceMinutes")}
                            </label>
                            <input
                                id={`gr-${worksheet.worksheetId}`}
                                type="number"
                                min={0}
                                className="form-control form-control-sm"
                                value={grace}
                                onChange={(e) => setGrace(e.target.value)}
                                placeholder="0"
                            />
                            <small className="text-muted">{t("schedule.graceHint")}</small>
                        </div>
                    </div>

                    {error && <div className="alert alert-danger py-1 px-2 mt-2 mb-0 small">{error}</div>}

                    <div className="d-flex gap-2 mt-2">
                        <button className="btn btn-sm btn-primary" onClick={save} disabled={saving}>
                            {saving ? t("schedule.saving") : t("schedule.save")}
                        </button>
                        <button
                            className="btn btn-sm btn-outline-secondary"
                            disabled={saving}
                            onClick={() => {
                                // Clearing every field restores the default:
                                // available at once, open forever, sharp deadline.
                                setReleaseAt("");
                                setDeadlineAt("");
                                setGrace("");
                            }}
                        >
                            {t("schedule.clear")}
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}
