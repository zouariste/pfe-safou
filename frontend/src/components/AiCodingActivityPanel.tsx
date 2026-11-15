import { useTranslation } from "react-i18next";

interface Props {
    onBackToParsons: () => void;
}

const PHASE_KEYS = ["specification", "prediction", "generation", "understanding", "verification", "improvement"] as const;

export default function AiCodingActivityPanel({ onBackToParsons }: Props) {
    const { t } = useTranslation();

    return (
        <div className="card border-primary-subtle shadow-sm mb-3">
            <div className="card-body">
                <div className="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
                    <h2 className="h4 mb-0">{t("aiCoding.title")}</h2>
                    <button type="button" className="btn btn-outline-secondary btn-sm" onClick={onBackToParsons}>
                        ← {t("aiCoding.backToParsons")}
                    </button>
                </div>

                <p className="text-muted">{t("aiCoding.description")}</p>

                <h3 className="h6 fw-semibold mt-4 mb-3">{t("aiCoding.phasesHeading")}</h3>
                <ol className="row g-3 list-unstyled mb-4">
                    {PHASE_KEYS.map((key, index) => (
                        <li key={key} className="col-12 col-md-6 col-lg-4">
                            <div className="border rounded p-3 h-100 bg-light">
                                <span className="badge text-bg-primary me-2">{index + 1}</span>
                                <span className="fw-medium">{t(`aiCoding.phases.${key}`)}</span>
                            </div>
                        </li>
                    ))}
                </ol>

                <div className="border rounded p-3 bg-white">
                    <h3 className="h6 fw-semibold mb-2">{t("aiCoding.specification.heading")}</h3>
                    <p className="small text-muted mb-3">{t("aiCoding.specification.intro")}</p>
                    <div className="placeholder-glow">
                        <span className="placeholder col-12 mb-2" style={{ height: "2.5rem" }} />
                        <span className="placeholder col-10 mb-2" style={{ height: "5rem" }} />
                        <span className="placeholder col-8" style={{ height: "2.5rem" }} />
                    </div>
                    <p className="small text-muted mt-3 mb-0">{t("aiCoding.specification.comingSoon")}</p>
                </div>
            </div>
        </div>
    );
}