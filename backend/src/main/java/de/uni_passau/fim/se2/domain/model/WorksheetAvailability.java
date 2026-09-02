/*
 * Copyright (C) 2026 CodeOwl contributors
 *
 * This file is part of CodeOwl.
 *
 * CodeOwl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * CodeOwl is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CodeOwl. If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_passau.fim.se2.domain.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Where a worksheet assignment sits relative to its schedule, for one class.
 *
 * Teachers always see every state; the distinctions matter for students.
 */
public enum WorksheetAvailability {

    /** Before the release date: students are not shown the worksheet at all. */
    SCHEDULED,

    /** Released, and either no deadline or still within it (plus any grace). */
    OPEN,

    /** Past the deadline and its grace: students may read the tasks, not solve them. */
    CLOSED;

    /** Whether a student may work on the tasks. */
    public boolean allowsSolving() {
        return this == OPEN;
    }

    /** Whether a student is shown the worksheet at all. */
    public boolean visibleToStudents() {
        return this != SCHEDULED;
    }

    /**
     * Resolves the state of one assignment.
     *
     * Each field is optional and absent means unconstrained, which is what an
     * assignment made before this feature existed will have:
     * no release means available immediately, no deadline means open
     * indefinitely, and no grace means the deadline is sharp.
     *
     * @param graceMinutes minutes allowed after the deadline; null or 0 is sharp
     */
    public static WorksheetAvailability of(Instant releaseAt, Instant deadlineAt,
                                           Integer graceMinutes, Instant now) {
        Instant moment = now != null ? now : Instant.now();

        if (releaseAt != null && moment.isBefore(releaseAt)) {
            return SCHEDULED;
        }
        if (deadlineAt == null) {
            return OPEN;
        }

        // The grace period extends the deadline; a null or zero grace leaves it
        // exactly where it is. Negative values are rejected by the database, but
        // clamp here too rather than letting one pull the deadline earlier.
        long grace = graceMinutes != null ? Math.max(0, graceMinutes) : 0;
        Instant closesAt = deadlineAt.plus(Duration.ofMinutes(grace));

        // On the boundary the worksheet is still open: a submission landing on
        // the exact second of the deadline should count.
        return moment.isAfter(closesAt) ? CLOSED : OPEN;
    }
}
