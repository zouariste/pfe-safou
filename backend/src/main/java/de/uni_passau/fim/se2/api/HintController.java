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
package de.uni_passau.fim.se2.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.uni_passau.fim.se2.application.service.OwnershipService;
import de.uni_passau.fim.se2.application.service.hints.HintLocale;
import de.uni_passau.fim.se2.application.service.hints.HintService;

@RestController
@RequestMapping("/api/hints")
public class HintController {

    private final HintService hintService;
    private final OwnershipService ownershipService;

    public HintController(HintService hintService, OwnershipService ownershipService) {
        this.hintService = hintService;
        this.ownershipService = ownershipService;
    }

    /**
     * Generates a hint for the student's current arrangement of a task.
     *
     * @param taskId  the task being solved
     * @param request the current block order, indent levels, requested hint level and UI locale
     * @return the hint text, the affected blocks, an explanation and the analysis findings
     */
    @PostMapping("/{taskId}")
    public ResponseEntity<HintService.HintResponse> getHint(
        @PathVariable UUID taskId,
        @RequestBody HintRequest request
    ) {
        UUID userId = ownershipService.getCurrentUserId();
        HintService.HintResponse response = hintService.getHint(
            taskId, request.blockOrder(), request.indentLevels(), request.hintLevel(), userId,
            HintLocale.from(request.locale())
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Hint availability for the current user on this task: whether the teacher enabled hints, the credit budget, and
     * what is left of it.
     *
     * This drives the buttons in the UI. It is not the enforcement point — HintService re-checks both on every actual
     * hint request.
     *
     * @param taskId the task being solved
     * @return whether hints are enabled, the credit budget and the credits left
     */
    @GetMapping("/{taskId}/config")
    public ResponseEntity<Map<String, Object>> getHintConfig(@PathVariable UUID taskId) {
        UUID userId = ownershipService.getCurrentUserId();
        HintService.HintStatus status = hintService.getStatus(taskId, userId);

        // HashMap rather than Map.of: the credit fields are null when the
        // worksheet grants unlimited hints, and Map.of rejects null values.
        Map<String, Object> body = new HashMap<>();
        body.put("hintsEnabled", status.hintsEnabled());
        body.put("creditsPerTask", status.creditsPerTask());
        body.put("creditsUsed", status.creditsUsed());
        body.put("creditsRemaining", status.creditsRemaining());
        return ResponseEntity.ok(body);
    }

    /**
     * @param locale the student's current UI language ("de"/"en"); optional, and absent means German, which is what
     *               hints were before.
     */
    public record HintRequest(List<String> blockOrder, List<Integer> indentLevels, int hintLevel, String locale) {
    }
}
