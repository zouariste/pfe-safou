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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorksheetAvailabilityTest {

    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    private static Instant minutes(long m) {
        return NOW.plus(m, ChronoUnit.MINUTES);
    }

    @Nested
    @DisplayName("when nothing is scheduled")
    class Unscheduled {

        @Test
        @DisplayName("an assignment with no dates is simply open")
        void openByDefault() {
            // Every assignment made before scheduling existed looks like this, so
            // it has to keep working exactly as before.
            assertEquals(WorksheetAvailability.OPEN,
                    WorksheetAvailability.of(null, null, null, NOW));
        }
    }

    @Nested
    @DisplayName("release date")
    class Release {

        @Test
        @DisplayName("before it, the worksheet is scheduled and hidden from students")
        void beforeRelease() {
            WorksheetAvailability a = WorksheetAvailability.of(minutes(10), null, null, NOW);
            assertAll(
                    () -> assertEquals(WorksheetAvailability.SCHEDULED, a),
                    () -> assertFalse(a.visibleToStudents()),
                    () -> assertFalse(a.allowsSolving()));
        }

        @Test
        @DisplayName("on the release instant it is already open")
        void atRelease() {
            assertEquals(WorksheetAvailability.OPEN,
                    WorksheetAvailability.of(NOW, null, null, NOW));
        }

        @Test
        @DisplayName("no release date means available at once")
        void noRelease() {
            assertEquals(WorksheetAvailability.OPEN,
                    WorksheetAvailability.of(null, minutes(60), null, NOW));
        }
    }

    @Nested
    @DisplayName("deadline")
    class Deadline {

        @Test
        @DisplayName("no deadline stays open indefinitely")
        void noDeadline() {
            assertEquals(WorksheetAvailability.OPEN,
                    WorksheetAvailability.of(minutes(-1000), null, null, NOW));
        }

        @Test
        @DisplayName("before the deadline it is open")
        void beforeDeadline() {
            assertEquals(WorksheetAvailability.OPEN,
                    WorksheetAvailability.of(null, minutes(1), null, NOW));
        }

        @Test
        @DisplayName("on the deadline instant it is still open")
        void atDeadline() {
            // Work landing on the exact second of the deadline should count.
            assertEquals(WorksheetAvailability.OPEN,
                    WorksheetAvailability.of(null, NOW, null, NOW));
        }

        @Test
        @DisplayName("past a sharp deadline it is closed")
        void afterSharpDeadline() {
            WorksheetAvailability a = WorksheetAvailability.of(null, minutes(-1), null, NOW);
            assertAll(
                    () -> assertEquals(WorksheetAvailability.CLOSED, a),
                    // Closed still means readable — students may look, not work.
                    () -> assertTrue(a.visibleToStudents()),
                    () -> assertFalse(a.allowsSolving()));
        }
    }

    @Nested
    @DisplayName("grace period")
    class Grace {

        @Test
        @DisplayName("extends the deadline by its minutes")
        void extendsDeadline() {
            // Deadline was 10 minutes ago, but a 30 minute grace still covers it.
            assertEquals(WorksheetAvailability.OPEN,
                    WorksheetAvailability.of(null, minutes(-10), 30, NOW));
        }

        @Test
        @DisplayName("closes once the grace has run out too")
        void closesAfterGrace() {
            assertEquals(WorksheetAvailability.CLOSED,
                    WorksheetAvailability.of(null, minutes(-40), 30, NOW));
        }

        @Test
        @DisplayName("on the last instant of the grace it is still open")
        void atGraceBoundary() {
            assertEquals(WorksheetAvailability.OPEN,
                    WorksheetAvailability.of(null, minutes(-30), 30, NOW));
        }

        @Test
        @DisplayName("no grace, or zero, leaves the deadline sharp")
        void noGraceIsSharp() {
            assertAll(
                    () -> assertEquals(WorksheetAvailability.CLOSED,
                            WorksheetAvailability.of(null, minutes(-1), null, NOW)),
                    () -> assertEquals(WorksheetAvailability.CLOSED,
                            WorksheetAvailability.of(null, minutes(-1), 0, NOW)));
        }

        @Test
        @DisplayName("a negative grace cannot pull the deadline earlier")
        void negativeGraceIsClamped() {
            // The database rejects these, but a stray value must not make a
            // worksheet close before its stated deadline.
            assertEquals(WorksheetAvailability.OPEN,
                    WorksheetAvailability.of(null, minutes(5), -60, NOW));
        }

        @Test
        @DisplayName("grace without a deadline changes nothing")
        void graceWithoutDeadline() {
            assertEquals(WorksheetAvailability.OPEN,
                    WorksheetAvailability.of(null, null, 30, NOW));
        }
    }

    @Nested
    @DisplayName("release and deadline together")
    class Combined {

        @Test
        @DisplayName("a window behaves as one would expect at each point")
        void wholeWindow() {
            Instant release = minutes(10);
            Instant deadline = minutes(60);
            assertAll(
                    () -> assertEquals(WorksheetAvailability.SCHEDULED,
                            WorksheetAvailability.of(release, deadline, 15, NOW)),
                    () -> assertEquals(WorksheetAvailability.OPEN,
                            WorksheetAvailability.of(release, deadline, 15, minutes(30))),
                    () -> assertEquals(WorksheetAvailability.OPEN,
                            WorksheetAvailability.of(release, deadline, 15, minutes(70))),
                    () -> assertEquals(WorksheetAvailability.CLOSED,
                            WorksheetAvailability.of(release, deadline, 15, minutes(80))));
        }

        @Test
        @DisplayName("being unreleased wins over a passed deadline")
        void releaseTakesPrecedence() {
            // Not reachable through the UI — the database forbids a deadline
            // before the release — but the order of the checks should be stated.
            assertEquals(WorksheetAvailability.SCHEDULED,
                    WorksheetAvailability.of(minutes(10), minutes(-10), null, NOW));
        }
    }
}
