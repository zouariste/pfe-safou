package de.uni_passau.fim.se2.domain.entities;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a log entry for a student's feedback interaction on a task.
 * <p>
 * Each time a learner clicks the “Feedback” button in the solver interface,
 * a new log entry is created. The entry records the associated task, the
 * correctness of the current solution at that moment, and the timestamp of
 * the interaction.
 * </p>
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackLogDTO {

    /**
     * The unique identifier of this feedback log entry.
     *
     * @return the feedback log ID
     */
    private UUID id;

    /**
     * The unique identifier of the task for which this feedback was triggered.
     *
     * @return the associated task ID
     */
    private UUID taskId;

    /**
     * Whether the task was solved correctly at the time of this feedback click.
     *
     * @return {@code true} if the current solution was fully correct,
     *         {@code false} otherwise
     */
    private boolean correct;

    /**
     * The user who clicked the feedback button (nullable — NULL if tracking
     * is disabled for the class or if the user is anonymous).
     */
    private UUID userId;

    /**
     * The class context in which the student solved the task (nullable).
     */
    private UUID classId;

    /**
     * The timestamp when the feedback button was clicked.
     *
     * @return the moment of interaction as a {@link LocalDateTime}
     */
    private LocalDateTime clickedAt;
}