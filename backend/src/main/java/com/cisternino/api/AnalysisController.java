package com.cisternino.api;

import com.cisternino.application.service.analysis.ChangeAnalysisService;
import com.cisternino.domain.entities.TaskChangeAnalysisDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final ChangeAnalysisService changeAnalysisService;

    public AnalysisController(ChangeAnalysisService changeAnalysisService) {
        this.changeAnalysisService = changeAnalysisService;
    }

    /**
     * Trigger analysis for a single task (on-demand).
     * Requires authentication — only owner of the task's sequence or admin.
     */
    @PostMapping("/task/{taskId}")
    public ResponseEntity<TaskChangeAnalysisDTO> analyzeTask(@PathVariable UUID taskId) {
        TaskChangeAnalysisDTO result = changeAnalysisService.analyzeTask(taskId);
        return ResponseEntity.ok(result);
    }

    /**
     * Batch analysis: analyze all tasks that have originals but no analysis yet.
     * Admin only.
     */
    @PostMapping("/batch")
    public ResponseEntity<List<TaskChangeAnalysisDTO>> batchAnalyze() {
        List<TaskChangeAnalysisDTO> results = changeAnalysisService.batchAnalyze();
        return ResponseEntity.ok(results);
    }

    /**
     * Get analysis result for a specific task.
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<TaskChangeAnalysisDTO> getAnalysis(@PathVariable UUID taskId) {
        return changeAnalysisService.getByTaskId(taskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Export all analyses as JSON (for Python/R/SPSS).
     * Admin only.
     */
    @GetMapping("/export")
    public ResponseEntity<List<TaskChangeAnalysisDTO>> exportAll() {
        return ResponseEntity.ok(changeAnalysisService.findAll());
    }

    /**
     * Aggregated summary statistics.
     * Admin only.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(changeAnalysisService.getSummary());
    }
}
