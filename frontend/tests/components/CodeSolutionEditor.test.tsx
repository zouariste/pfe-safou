import { beforeAll, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import i18n from "../../src/i18n/index.ts";
import CodeSolutionEditor from "../../src/components/CodeSolutionEditor.tsx";

function renderEditor(code: string) {
    render(<CodeSolutionEditor code={code} onCodeChange={vi.fn()} splits={[]} onSplitsChange={vi.fn()} />);
}

describe("CodeSolutionEditor group warning", () => {
    // The real translations: the warning has to name the groups, not show a raw placeholder.
    beforeAll(async () => {
        await i18n.changeLanguage("de");
    });

    it("names the groups that have nothing to swap with", () => {
        renderEditor("def a(): #group:einlesen\ndef b(): #group:jahr");
        const warning = screen.getByRole("status");
        expect(warning).toHaveTextContent("einlesen, jahr");
        expect(warning).toHaveTextContent("#group:block_1");
    });

    it("names the groups that are split up", () => {
        renderEditor("a = 1 #group:f_1\nprint('x')\nb = 2 #group:f_1\nc = 3 #group:f_2");
        const warning = screen.getByRole("status");
        expect(warning).toHaveTextContent("f_1");
        expect(warning).toHaveTextContent("aufgeteilt");
    });

    it("stays quiet for clusters that can swap", () => {
        renderEditor("def a(): #group:f_1\ndef b(): #group:f_2");
        expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });
});
