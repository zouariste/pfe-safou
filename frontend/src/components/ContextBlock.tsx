import { useTranslation } from "react-i18next";
import { CONTEXT_STYLE } from "../lib/contextLines";

/** AP22: a locked, read-only context block shown above or below a puzzle. */
export default function ContextBlock({ code }: { code: string }) {
    const { t } = useTranslation();
    return (
        <div
            className="rounded px-3 py-2 mb-1"
            style={{ ...CONTEXT_STYLE, whiteSpace: "pre", overflowX: "auto", userSelect: "none" }}
        >
            <div
                className="text-uppercase fw-semibold mb-1"
                style={{ fontSize: "0.65rem", letterSpacing: "0.05em", fontFamily: "system-ui, sans-serif" }}
            >
                <i className="bi bi-lock me-1"></i>
                {t("solve.contextLabel")}
            </div>
            {code}
        </div>
    );
}
