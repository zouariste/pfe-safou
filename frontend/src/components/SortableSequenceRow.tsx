import type { ReactNode } from "react";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { ListGroup } from "react-bootstrap";

interface SortableSequenceRowProps {
    /** The sequence id — doubles as the dnd-kit sortable item id. */
    id: string;
    /**
     * Whether this row can be picked up at all. False renders a plain, static row with no
     * handle — a student or a non-owner viewing someone else's worksheet has no business
     * reordering it, and the worksheet's own "sequences" endpoint enforces the same rule
     * server-side.
     */
    draggable: boolean;
    dragHandleLabel: string;
    className?: string;
    title?: string;
    children: ReactNode;
}

/**
 * One row in a worksheet's sequence list, made sortable via dnd-kit.
 *
 * <p>The drag handle is a separate element carrying dnd-kit's listeners, deliberately not the
 * whole row: the row's own content is still a click target that navigates to the sequence, and
 * attaching drag listeners to it too would turn every click into a drag-start race.
 */
export default function SortableSequenceRow({
    id,
    draggable,
    dragHandleLabel,
    className,
    title,
    children,
}: SortableSequenceRowProps) {
    const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
        id,
        disabled: !draggable,
    });

    const style = {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.5 : 1,
    };

    return (
        <ListGroup.Item ref={setNodeRef} style={style} className={className} title={title}>
            <div className="d-flex align-items-center gap-2">
                {draggable && (
                    <span
                        {...attributes}
                        {...listeners}
                        className="text-muted flex-shrink-0"
                        style={{ cursor: isDragging ? "grabbing" : "grab", touchAction: "none" }}
                        aria-label={dragHandleLabel}
                        title={dragHandleLabel}
                    >
                        <i className="bi bi-list fs-5"></i>
                    </span>
                )}
                <div className="flex-grow-1 d-flex justify-content-between align-items-center">
                    {children}
                </div>
            </div>
        </ListGroup.Item>
    );
}
