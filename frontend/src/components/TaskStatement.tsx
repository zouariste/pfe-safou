import React from "react";
import ReactMarkdown, { type Components } from "react-markdown";
import remarkBreaks from "remark-breaks";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import "katex/dist/katex.min.css";

/**
 * A task's problem statement, rendered as Markdown with LaTeX math ($…$ inline, $$…$$ as a block).
 *
 * <p>The manual task editor promises Markdown, so every place that shows a statement in full goes
 * through here. Single line breaks are kept (remark-breaks), as they were when statements were shown
 * as pre-wrapped plain text, so a statement written without Markdown in mind looks the same as before.
 * Raw HTML is not rendered, only shown as text, which keeps a teacher's `List<String>` from being
 * parsed as a tag and keeps statements from injecting markup into a student's page.
 */
const components: Components = {
    // Opening a link must not navigate away from a half-solved puzzle.
    a: ({ href, title, children }) => (
        <a href={href} title={title} target="_blank" rel="noopener noreferrer">{children}</a>
    ),
};

interface Props {
    statement: string;
    className?: string;
    style?: React.CSSProperties;
}

export const TaskStatement: React.FC<Props> = ({ statement, className, style }) => (
    <div className={["task-statement", className].filter(Boolean).join(" ")} style={style}>
        <ReactMarkdown
            remarkPlugins={[remarkGfm, remarkMath, remarkBreaks]}
            rehypePlugins={[rehypeKatex]}
            components={components}
        >
            {statement}
        </ReactMarkdown>
    </div>
);

export default TaskStatement;
