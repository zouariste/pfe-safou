import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import AiCodingActivityPanel from "../components/AiCodingActivityPanel.tsx";
import { useHomePath } from "../hooks/useHomePath.ts";
import { usePageTitle } from "../hooks/usePageTitle.ts";

export default function AiCodingActivityPage() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const homePath = useHomePath();
    usePageTitle(t("aiCoding.title"));

    return (
        <div className="container py-4">
            <AiCodingActivityPanel onBackToParsons={() => navigate(homePath)} />
        </div>
    );
}