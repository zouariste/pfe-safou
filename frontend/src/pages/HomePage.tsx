import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { api, LLM_TIMEOUT_MS, apiErrorMessage, type ApiError } from "../lib/api.ts";
import "bootstrap/dist/css/bootstrap.min.css";
import { SequenceView } from "../components/SequenceView.tsx";
import type { TieredTaskSequence } from "../types/models.ts";
import { describeOverLongTasks, generationOutcome, MAX_GENERATION_ATTEMPTS } from "../lib/locWarning.ts";
import { useNavigate } from "react-router-dom";
import { usePageTitle } from "../hooks/usePageTitle.ts";
import ConceptPicker from "../components/ConceptPicker";
import { useConceptCatalogue } from "../hooks/useConceptCatalogue";

export default function HomePage() {
    const { t } = useTranslation();
    usePageTitle(t("home.pageTitle"));

    const [codeSnippet, setCodeSnippet] = useState("");
    const [problemStatement, setProblemStatement] = useState("");
    const [language, setLanguage] = useState("");
    const [targetLanguage, setTargetLanguage] = useState("de");
    const [concepts, setConcepts] = useState<string[]>([]);
    const conceptCatalogue = useConceptCatalogue();
    const [distractorsEnabled, setDistractorsEnabled] = useState(false);
    const [contextModeEnabled, setContextModeEnabled] = useState(false);
    const [splitEnabled, setSplitEnabled] = useState(false); // AP31
    // AP20: Generation Constraints
    const [showAdvancedOptions, setShowAdvancedOptions] = useState(false);
    const [blacklist, setBlacklist] = useState<string[]>([]);
    const [whitelist, setWhitelist] = useState<string[]>([]);
    const [difficultySlope, setDifficultySlope] = useState("moderate");
    const [additionalContext, setAdditionalContext] = useState("");
    // AP21: Five-Tier Mode
    const [tierCount, setTierCount] = useState<number>(3);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [result, setResult] = useState<TieredTaskSequence | null>(null);
    // A generated sequence with tasks over the line limit, waiting for the teacher to try again or
    // open it. Keeps the request so "try again" sends exactly the same inputs.
    const [overLong, setOverLong] = useState<{
        seq: TieredTaskSequence;
        payload: Record<string, unknown>;
        attempt: number;
    } | null>(null);
    const [discarding, setDiscarding] = useState(false);
    const [showModal, setShowModal] = useState(false);
    const [worksheetName, setWorksheetName] = useState("");
    const [worksheetDesc, setWorksheetDesc] = useState("");
    const [creating, setCreating] = useState(false);
    const [modalError, setModalError] = useState<string | null>(null);

    const navigate = useNavigate();

    function handleCreateWorksheetOpen() {
        setShowModal(true);
        setWorksheetName("");
        setWorksheetDesc("");
        setModalError(null);
    }

    async function handleCreateWorksheetSubmit() {
        if (!worksheetName.trim()) {
            setModalError(t("home.worksheetNameRequired"));
            return;
        }

        setCreating(true);
        setModalError(null);

        try {
            const { data } = await api.post("/worksheets", {
                name: worksheetName.trim(),
                description: worksheetDesc.trim(),
            });
            setShowModal(false);
            navigate(`/worksheets/${data.id}`);
        } catch (err) {
            setModalError(apiErrorMessage(err, "Error creating worksheet"));
        } finally {
            setCreating(false);
        }
    }

    // submit with Ctrl/Cmd + Enter
    useEffect(() => {
        const onKey = (e: KeyboardEvent) => {
            if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "enter" && !loading) {
                void handleSubmit();
            }
        };
        window.addEventListener("keydown", onKey);
        return () => window.removeEventListener("keydown", onKey);
    }, [loading, codeSnippet, problemStatement, language, concepts]);

    // Kept as a name so the rest of the file reads unchanged; the picker
    // already hands over a clean, de-duplicated list.
    const conceptTokens = concepts;

    const handleSubmit = async (e?: React.FormEvent) => {
        e?.preventDefault();
        const payload: Record<string, unknown> = {};
        if (codeSnippet.trim() !== "") payload.codeSnippet = codeSnippet;
        if (problemStatement.trim() !== "") payload.problemStatement = problemStatement;
        if (language.trim() !== "") payload.language = language;
        if (conceptTokens.length) payload.concepts = conceptTokens;
        payload.targetLanguage = targetLanguage;
        payload.distractorsEnabled = distractorsEnabled;
        payload.contextModeEnabled = contextModeEnabled;
        payload.splitEnabled = splitEnabled; // AP31

        // AP20: Attach constraints if any are set
        if (blacklist.length) payload.blacklist = blacklist;
        if (whitelist.length) payload.whitelist = whitelist;
        if (difficultySlope && difficultySlope !== "moderate") payload.difficultySlope = difficultySlope;
        if (additionalContext.trim()) payload.additionalContext = additionalContext.trim();
        // AP21: Five-Tier Mode
        if (tierCount !== 3) payload.tierCount = tierCount;

        await generate(payload, 1);
    };

    const openSequence = (seq: TieredTaskSequence) => {
        setOverLong(null);
        setResult(seq);
        localStorage.setItem("lastSequence", JSON.stringify(seq));
        // The sequence page asks whether to keep a sequence that is still over the limit.
        navigate(`/sequence/${seq.id}`, seq.locWarning ? { state: { locWarning: seq.locWarning } } : undefined);
    };

    // Discards the over-long sequence and generates again with the same inputs.
    const retryOverLong = async () => {
        if (!overLong) return;
        const { seq, payload, attempt } = overLong;
        setDiscarding(true);
        try {
            await api.delete(`/sequence/${seq.id}`);
        } catch (err) {
            setError(apiErrorMessage(err, "Error discarding the sequence"));
            return;
        } finally {
            setDiscarding(false);
        }
        await generate(payload, attempt + 1);
    };

    const generate = async (payload: Record<string, unknown>, attempt: number) => {
        setError(null);
        setResult(null);
        setOverLong(null);
        setLoading(true);

        try {
            const response = await api.post("/tasks/generate", payload, { timeout: LLM_TIMEOUT_MS });
            const seq: TieredTaskSequence = response.data;
            if (generationOutcome(seq.locWarning, attempt) === "offerRetry") {
                setOverLong({ seq, payload, attempt });
                return;
            }
            openSequence(seq);

            // scroll preview into view on small screens
            setTimeout(() => {
                document.getElementById("preview-card")?.scrollIntoView({ behavior: "smooth", block: "start" });
            }, 50);
        } catch (caught) {
            const err = caught as ApiError;
            // The backend sends a stable `code` for the errors it expects us to explain
            // properly; everything else still falls back to its (German) message, and then
            // to whatever axios reports if the request never reached the server.
            const code = err.response?.data?.code;
            setError(
                code === "UNSUPPORTED_LANGUAGE"
                    ? t("home.errorUnsupportedLanguage")
                    : err.response?.data?.message || err.message || "Error during API request"
            );
        } finally {
            setLoading(false);
        }
    };

    const clearAll = () => {
        setCodeSnippet("");
        setProblemStatement("");
        setLanguage("");
        setTargetLanguage("de");
        setConcepts([]);
        setDistractorsEnabled(false);
        setContextModeEnabled(false);
        setBlacklist([]);
        setWhitelist([]);
        setDifficultySlope("moderate");
        setAdditionalContext("");
        setShowAdvancedOptions(false);
        setTierCount(3);
        setError(null);
        setResult(null);
        setOverLong(null);
    };

    return (
        <div className="container py-4" style={{ maxWidth: 1100 }}>
            {/* HERO */}
            <div
                className="rounded-4 p-4 p-md-5 mb-4 text-white"
                style={{
                    background:
                        "radial-gradient(1200px 400px at -10% -50%, #6f42c1 0%, transparent 60%), radial-gradient(1000px 400px at 110% -50%, #0d6efd 0%, transparent 60%), linear-gradient(135deg, #0d6efd 0%, #6f42c1 100%)",
                    boxShadow: "0 10px 30px rgba(13,110,253,.25)",
                }}
            >
                <div className="d-flex align-items-center gap-3">
                    <span style={{ fontSize: 40, lineHeight: 1 }}>🧩</span>
                    <div>
                        <h1 className="h2 fw-bold mb-1">{t("home.title")}</h1>
                        <p className="mb-0 opacity-75">
                            {t("home.subtitle")}
                        </p>
                    </div>
                </div>
            </div>

            {/* CONTENT GRID */}
            <div className="row gy-4">
                {/* FORM CARD */}
                <div className="col-12 col-lg-6">
                    <div className="card shadow-sm border-0 rounded-4">
                        <div className="card-body">
                            <form onSubmit={handleSubmit}>

                                {/* Code Snippet */}
                                <div className="mb-3">
                                    <label className="form-label fw-semibold">
                                        {t("home.codeSnippet")}
                                    </label>
                                    <div className="input-group">
                                        <span className="input-group-text">{"</>"}</span>
                                        <textarea
                                            className="form-control"
                                            rows={5}
                                            value={codeSnippet}
                                            onChange={(e) => setCodeSnippet(e.target.value)}
                                            placeholder={t("home.pasteCode")}
                                        />
                                    </div>
                                    <div className="form-text d-flex justify-content-between">
                                        <span>{t("home.characters", { count: codeSnippet.length })}</span>
                                    </div>
                                </div>

                                {/* Problem Statement */}
                                <div className="form-floating mb-3">
                                    <input
                                        id="problemInput"
                                        type="text"
                                        className="form-control"
                                        value={problemStatement}
                                        onChange={(e) => setProblemStatement(e.target.value)}
                                        placeholder={t("home.describeProblem")}
                                    />
                                    <label htmlFor="problemInput">{t("home.problemStatement")}</label>
                                </div>

                                {/* Language */}
                                <div className="input-group mb-3">
                                    <span className="input-group-text">🌐</span>
                                    <div className="form-floating">
                                        {/* Values are the backend's canonical SupportedLanguage names;
                                            empty leaves it to the backend to detect from the code. */}
                                        <select
                                            id="langSelect"
                                            className="form-select"
                                            value={language}
                                            onChange={(e) => setLanguage(e.target.value)}
                                        >
                                            <option value="">{t("home.programmingLanguageAuto")}</option>
                                            <option value="java">Java</option>
                                            <option value="python">Python</option>
                                        </select>
                                        <label htmlFor="langSelect">{t("home.programmingLanguage")}</label>
                                    </div>
                                </div>

                                {/* Target Language */}
                                <div className="input-group mb-3">
                                    <span className="input-group-text">🗣️</span>
                                    <div className="form-floating">
                                        <select
                                            id="targetLangSelect"
                                            className="form-select"
                                            value={targetLanguage}
                                            onChange={(e) => setTargetLanguage(e.target.value)}
                                        >
                                            <option value="de">{t("home.targetLanguageDe")}</option>
                                            <option value="en">{t("home.targetLanguageEn")}</option>
                                        </select>
                                        <label htmlFor="targetLangSelect">{t("home.targetLanguage")}</label>
                                    </div>
                                </div>

                                {/* Concepts — picked from the catalogue, or typed in full */}
                                <label className="form-label" htmlFor="homeConcepts">
                                    🏷️ {t("home.concepts")}
                                </label>
                                <ConceptPicker
                                    inputId="homeConcepts"
                                    value={concepts}
                                    catalogue={conceptCatalogue}
                                    onChange={setConcepts}
                                />

                                {/* AP15: Distractors toggle */}
                                <div className="form-check form-switch mt-3 mb-0">
                                    <input
                                        className="form-check-input"
                                        type="checkbox"
                                        id="distractorsToggle"
                                        checked={distractorsEnabled}
                                        onChange={(e) => setDistractorsEnabled(e.target.checked)}
                                    />
                                    <label className="form-check-label" htmlFor="distractorsToggle">
                                        {t("home.distractorsEnabled")}
                                    </label>
                                </div>

                                {/* AP22: Context mode toggle */}
                                <div className="form-check form-switch mt-2 mb-0">
                                    <input
                                        className="form-check-input"
                                        type="checkbox"
                                        id="contextModeToggle"
                                        checked={contextModeEnabled}
                                        onChange={(e) => setContextModeEnabled(e.target.checked)}
                                    />
                                    <label className="form-check-label" htmlFor="contextModeToggle">
                                        {t("home.contextModeEnabled")}
                                    </label>
                                </div>

                                {/* AP31: Inline-split toggle */}
                                <div className="form-check form-switch mt-2 mb-0">
                                    <input
                                        className="form-check-input"
                                        type="checkbox"
                                        id="splitToggle"
                                        checked={splitEnabled}
                                        onChange={(e) => setSplitEnabled(e.target.checked)}
                                    />
                                    <label className="form-check-label" htmlFor="splitToggle">
                                        {t("home.splitEnabled", "Generate inline-split blocks")}
                                    </label>
                                </div>

                                {/* AP20: Advanced Generation Constraints */}
                                <div className="mt-3">
                                    <button
                                        type="button"
                                        className="btn btn-sm btn-outline-secondary d-flex align-items-center gap-1"
                                        onClick={() => setShowAdvancedOptions(!showAdvancedOptions)}
                                    >
                                        <span style={{ transition: "transform 0.2s", transform: showAdvancedOptions ? "rotate(90deg)" : "rotate(0deg)" }}>
                                            ▶
                                        </span>
                                        {t("home.advancedOptions", "Advanced Options")}
                                    </button>

                                    {showAdvancedOptions && (
                                        <div className="border rounded-3 p-3 mt-2" style={{ background: "#f8f9fa" }}>
                                            {/* Blacklist */}
                                            <div className="mb-3">
                                                <label htmlFor="homeBlacklist" className="form-label fw-semibold text-danger-emphasis" style={{ fontSize: "0.85rem" }}>
                                                    🚫 {t("home.blacklist", "Blacklist (forbidden constructs)")}
                                                </label>
                                                {/* Suggests from the concept catalogue; a name the AI should
                                                    avoid may also be typed in full. */}
                                                <ConceptPicker
                                                    inputId="homeBlacklist"
                                                    value={blacklist}
                                                    catalogue={conceptCatalogue}
                                                    onChange={setBlacklist}
                                                    exclude={whitelist}
                                                    placeholder={t("home.blacklistPlaceholder")}
                                                    newHint={(name) => t("home.constraintNew", { name })}
                                                />
                                                <div className="form-text">{t("home.blacklistHint")}</div>
                                            </div>

                                            {/* Whitelist */}
                                            <div className="mb-3">
                                                <label htmlFor="homeWhitelist" className="form-label fw-semibold text-success-emphasis" style={{ fontSize: "0.85rem" }}>
                                                    ✅ {t("home.whitelist", "Whitelist (allowed constructs only)")}
                                                </label>
                                                <ConceptPicker
                                                    inputId="homeWhitelist"
                                                    value={whitelist}
                                                    catalogue={conceptCatalogue}
                                                    onChange={setWhitelist}
                                                    exclude={blacklist}
                                                    placeholder={t("home.whitelistPlaceholder")}
                                                    newHint={(name) => t("home.constraintNew", { name })}
                                                />
                                                <div className="form-text">{t("home.whitelistHint")}</div>
                                            </div>

                                            {/* Difficulty Slope */}
                                            <div className="mb-3">
                                                <label className="form-label fw-semibold" style={{ fontSize: "0.85rem" }}>
                                                    📈 {t("home.difficultySlope", "Difficulty Slope")}
                                                </label>
                                                <select
                                                    className="form-select form-select-sm"
                                                    value={difficultySlope}
                                                    onChange={(e) => setDifficultySlope(e.target.value)}
                                                >
                                                    <option value="gentle">{t("home.slopeGentle", "Gentle — gradual increase")}</option>
                                                    <option value="moderate">{t("home.slopeModerate", "Moderate — standard (default)")}</option>
                                                    <option value="steep">{t("home.slopeSteep", "Steep — big jump to Tier 3")}</option>
                                                </select>
                                            </div>

                                            {/* Additional Context / Free-Text */}
                                            <div className="mb-3">
                                                <label className="form-label fw-semibold" style={{ fontSize: "0.85rem" }}>
                                                    💬 {t("home.additionalContext", "Teacher Instructions")}
                                                </label>
                                                <textarea
                                                    className="form-control form-control-sm"
                                                    rows={2}
                                                    value={additionalContext}
                                                    onChange={(e) => setAdditionalContext(e.target.value)}
                                                    placeholder={t("home.additionalContextPlaceholder", "e.g. Use German variable names, Focus on string manipulation")}
                                                />
                                                <div className="form-text">{t("home.additionalContextHint", "Free-text instructions injected into the generation prompt.")}</div>
                                            </div>

                                            {/* AP21: Five-Tier Mode */}
                                            <div className="mb-0">
                                                <label className="form-label fw-semibold" style={{ fontSize: "0.85rem" }}>
                                                    🔢 {t("home.tierCount", "Number of Tiers")}
                                                </label>
                                                <select
                                                    className="form-select form-select-sm"
                                                    value={tierCount}
                                                    onChange={(e) => setTierCount(Number(e.target.value))}
                                                >
                                                    <option value={3}>{t("home.tierCount3", "3 Tiers — Standard (Easy / Medium / Hard)")}</option>
                                                    <option value={5}>{t("home.tierCount5", "5 Tiers — Extended (Recognition → Synthesis)")}</option>
                                                </select>
                                                <div className="form-text">{t("home.tierCountHint", "5-Tier mode uses a dedicated pedagogical progression prompt.")}</div>
                                            </div>
                                        </div>
                                    )}
                                </div>

                                {/* Actions */}
                                <div className="d-flex flex-wrap gap-2 mt-4">
                                    <button type="submit" className="btn btn-primary px-3" disabled={loading}>
                                        {loading ? (
                                            <>
                                                <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true" />
                                                {t("home.generating")}
                                            </>
                                        ) : (
                                            t("home.generateSequence")
                                        )}
                                    </button>

                                    <button type="button" className="btn btn-outline-danger ms-auto" onClick={clearAll} disabled={loading}>
                                        {t("home.reset")}
                                    </button>

                                    <button
                                        type="button"
                                        className="btn btn-outline-secondary ms-auto"
                                        style={{ whiteSpace: "nowrap" }}
                                        onClick={handleCreateWorksheetOpen}
                                        disabled={loading}
                                        title={t("home.createWorksheet")}
                                    >
                                        ➕ {t("home.worksheet")}
                                    </button>
                                </div>
                            </form>

                            {overLong?.seq.locWarning && (
                                <div className="alert alert-warning mt-4 mb-0" role="alert">
                                    <strong>
                                        {t("home.locWarningTitle", { attempt: overLong.attempt, max: MAX_GENERATION_ATTEMPTS })}
                                    </strong>
                                    <div className="mt-1">
                                        {t("home.locWarningBody", {
                                            tasks: describeOverLongTasks(overLong.seq.locWarning, (tier, lines) =>
                                                t("home.locWarningTask", { tier, lines })
                                            ),
                                            max: overLong.seq.locWarning.maxLoc,
                                        })}
                                    </div>
                                    <div className="d-flex flex-wrap gap-2 mt-3">
                                        <button
                                            type="button"
                                            className="btn btn-sm btn-primary"
                                            onClick={() => void retryOverLong()}
                                            disabled={loading || discarding}
                                        >
                                            {t("home.locWarningRetry")}
                                        </button>
                                        <button
                                            type="button"
                                            className="btn btn-sm btn-outline-secondary"
                                            onClick={() => openSequence(overLong.seq)}
                                            disabled={loading || discarding}
                                        >
                                            {t("home.locWarningShow")}
                                        </button>
                                    </div>
                                </div>
                            )}

                            {error && (
                                <div className="alert alert-danger d-flex align-items-start gap-2 mt-4 mb-0">
                                    <span>⚠️</span>
                                    <div>
                                        <strong>{t("home.error")}:</strong> {error}
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                {/* PREVIEW / TIPS */}
                <div className="col-12 col-lg-6" id="preview-card">
                    <div className="card shadow-sm border-0 rounded-4 h-100">
                        <div className="card-body">
                            {result ? (
                                <>
                                    <div className="d-flex align-items-center justify-content-between mb-3">
                                        <h2 className="h5 mb-0">{t("home.preview")}</h2>
                                        <span className="badge text-bg-primary">{t("home.new")}</span>
                                    </div>
                                    <SequenceView sequence={result} />
                                </>
                            ) : (
                                <>
                                    <h2 className="h5">{t("home.tips")}</h2>
                                    <ul className="mb-0 text-muted">
                                        <li dangerouslySetInnerHTML={{ __html: t("home.tip1") }} />
                                        <li dangerouslySetInnerHTML={{ __html: t("home.tip2") }} />
                                        <li dangerouslySetInnerHTML={{ __html: t("home.tip3") }} />
                                        <li>{t("home.tip4")}</li>
                                        <li>{t("home.tip5")}</li>
                                        <li>{t("home.tip6")}</li>
                                        <li>{t("home.tip7")}</li>
                                    </ul>

                                    <hr className="my-4" />
                                </>
                            )}
                        </div>
                    </div>
                </div>
            </div>
            {/* WORKSHEET MODAL */}
            {showModal && (
                <>
                    <div className="modal fade show" style={{ display: "block" }} tabIndex={-1}>
                        <div className="modal-dialog modal-dialog-centered">
                            <div className="modal-content rounded-4 shadow">
                                <div className="modal-header">
                                    <h5 className="modal-title">{t("home.createWorksheet")}</h5>
                                    <button type="button" className="btn-close" onClick={() => setShowModal(false)}></button>
                                </div>
                                <div className="modal-body">
                                    <div className="mb-3">
                                        <label className="form-label fw-semibold">{t("home.worksheetName")}</label>
                                        <input
                                            type="text"
                                            className="form-control"
                                            value={worksheetName}
                                            onChange={(e) => setWorksheetName(e.target.value)}
                                            placeholder={t("home.worksheetNamePlaceholder")}
                                            disabled={creating}
                                        />
                                    </div>
                                    <div className="mb-3">
                                        <label className="form-label fw-semibold">{t("home.worksheetDesc")}</label>
                                        <textarea
                                            className="form-control"
                                            rows={3}
                                            value={worksheetDesc}
                                            onChange={(e) => setWorksheetDesc(e.target.value)}
                                            placeholder={t("home.worksheetDescPlaceholder")}
                                            disabled={creating}
                                        />
                                    </div>
                                    {modalError && <div className="alert alert-danger py-2">{modalError}</div>}
                                </div>
                                <div className="modal-footer">
                                    <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)} disabled={creating}>
                                        {t("home.cancel")}
                                    </button>
                                    <button type="button" className="btn btn-primary" onClick={handleCreateWorksheetSubmit} disabled={creating}>
                                        {creating ? (
                                            <>
                                                <span className="spinner-border spinner-border-sm me-2" />
                                                {t("home.creating")}
                                            </>
                                        ) : (
                                            t("home.createWorksheetBtn")
                                        )}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div className="modal-backdrop fade show"></div>
                </>
            )}
        </div>
    );
}
