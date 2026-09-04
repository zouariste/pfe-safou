import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import WorksheetPage from "../../src/pages/WorksheetPage.tsx";
import { api } from "../../src/lib/api.ts";

/**
 * What a visitor is told when a worksheet will not open.
 *
 * <p>Narrow on purpose. The page is large and mostly about editing; this covers only the load
 * failure, because that is the path a stranger following a link takes and the one that broke.
 *
 * <p>The bug: {@code ResponseEntity.notFound().build()} sends an empty body, so
 * {@link apiErrorMessage} found nothing to report and fell through to axios's own English
 * "Request failed with status code 404". Someone opening a link to a worksheet whose owner had
 * just made it private read a stack-trace fragment in a German UI. Both helpers are therefore
 * the real implementations here -- mocking them would test the mock, and the defect lived
 * exactly in how the two behave together.
 */
vi.mock("react-i18next", () => ({
    useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock("../../src/lib/api.ts", async (importOriginal) => {
    const actual = await importOriginal<typeof import("../../src/lib/api.ts")>();
    return {
        ...actual,
        api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
    };
});

vi.mock("../../src/context/AuthContext.tsx", () => ({
    useAuth: () => ({
        user: null,
        isLoggedIn: false,
        isTeacher: false,
        isAdmin: false,
        isStudent: false,
    }),
}));

const get = api.get as unknown as ReturnType<typeof vi.fn>;

/** An axios rejection as the interceptor hands it on: a status, and for 404 no body at all. */
function httpError(status: number, data?: unknown) {
    return {
        message: `Request failed with status code ${status}`,
        response: { status, data },
    };
}

function renderPage() {
    return render(
        <MemoryRouter initialEntries={["/worksheets/w-1"]}>
            <Routes>
                <Route path="/worksheets/:sheetId" element={<WorksheetPage />} />
            </Routes>
        </MemoryRouter>,
    );
}

describe("WorksheetPage, when the worksheet does not load", () => {
    beforeEach(() => {
        get.mockReset();
    });

    it("says the worksheet is not available rather than quoting axios", async () => {
        get.mockRejectedValue(httpError(404));
        renderPage();

        await waitFor(() => expect(screen.getByText("worksheet.notAvailable")).toBeInTheDocument());
    });

    it("never shows the raw transport message on a 404", async () => {
        // The regression itself, asserted directly: the empty body is what let axios's English
        // sentence reach the page, and that is what must not come back.
        get.mockRejectedValue(httpError(404));
        renderPage();

        await waitFor(() => expect(screen.getByText("worksheet.notAvailable")).toBeInTheDocument());
        expect(screen.queryByText(/Request failed with status code/)).not.toBeInTheDocument();
    });

    it("still reports what the server said for errors that are not 404", async () => {
        // The 404 branch must stay a special case. If it swallowed every failure, a broken
        // server would look to the visitor exactly like a worksheet that was taken down.
        get.mockRejectedValue(httpError(500, { message: "Datenbank nicht erreichbar" }));
        renderPage();

        await waitFor(() =>
            expect(screen.getByText("Datenbank nicht erreichbar")).toBeInTheDocument());
        expect(screen.queryByText("worksheet.notAvailable")).not.toBeInTheDocument();
    });

    it("shows the loading state until the request settles", async () => {
        get.mockReturnValue(new Promise(() => {})); // never resolves
        renderPage();

        expect(screen.getByText("common.loading")).toBeInTheDocument();
    });
});
