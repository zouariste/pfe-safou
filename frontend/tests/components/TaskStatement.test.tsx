import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";
import TaskStatement from "../../src/components/TaskStatement.tsx";

describe("TaskStatement", () => {
    it("renders Markdown emphasis, code and lists instead of showing the syntax", () => {
        const { container } = render(
            <TaskStatement statement={"Write **one** function `sum`:\n\n- adds\n- returns"} />,
        );

        expect(container.querySelector("strong")?.textContent).toBe("one");
        expect(container.querySelector("code")?.textContent).toBe("sum");
        expect(container.querySelectorAll("li")).toHaveLength(2);
        expect(container.textContent).not.toContain("**");
    });

    it("renders $…$ as math rather than as dollar signs", () => {
        const { container } = render(<TaskStatement statement={"Compute $x^2 + 1$ for every x."} />);

        expect(container.querySelector(".katex")).not.toBeNull();
        expect(container.textContent).not.toContain("$");
    });

    it("keeps single line breaks, as the plain-text rendering did", () => {
        const { container } = render(<TaskStatement statement={"first line\nsecond line"} />);

        expect(container.querySelector("br")).not.toBeNull();
    });

    it("shows generic types as text instead of parsing them as HTML", () => {
        const { container } = render(
            <TaskStatement statement={"Given a List<String> and a HashMap<Character,Integer>, count."} />,
        );

        expect(container.textContent).toContain("List<String>");
        expect(container.textContent).toContain("HashMap<Character,Integer>");
    });

    it("opens links in a new tab so a half-solved puzzle is not lost", () => {
        const { container } = render(<TaskStatement statement={"See [the docs](https://example.org)."} />);

        const link = container.querySelector("a");
        expect(link?.getAttribute("target")).toBe("_blank");
        expect(link?.getAttribute("rel")).toContain("noopener");
    });
});
