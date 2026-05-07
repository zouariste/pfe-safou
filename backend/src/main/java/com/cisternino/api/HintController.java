package com.cisternino.api;

import com.cisternino.application.service.OwnershipService;
import com.cisternino.application.service.hints.HintService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/hints")
public class HintController {

    private final HintService hintService;
    private final OwnershipService ownershipService;

    public HintController(HintService hintService, OwnershipService ownershipService) {
        this.hintService = hintService;
        this.ownershipService = ownershipService;
    }

    @PostMapping("/{taskId}")
    public ResponseEntity<HintService.HintResponse> getHint(
            @PathVariable UUID taskId,
            @RequestBody HintRequest request) {
        UUID userId = ownershipService.getCurrentUserId();
        HintService.HintResponse response = hintService.getHint(
            taskId, request.blockOrder(), request.indentLevels(), request.hintLevel(), userId
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{taskId}/config")
    public ResponseEntity<Map<String, Object>> getHintConfig(@PathVariable UUID taskId) {
        boolean enabled = hintService.isHintsEnabled(taskId);
        return ResponseEntity.ok(Map.of("hintsEnabled", enabled));
    }

    public record HintRequest(List<String> blockOrder, List<Integer> indentLevels, int hintLevel) {}
}
