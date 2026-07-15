package de.uni_passau.fim.se2.domain.interfaces;

import java.util.List;
import java.util.Map;

/**
 * Read-only access to research-relevant tables.
 * Each method returns a list of column-name → value maps.
 * Implementations MUST exclude PII columns from the users table.
 */
public interface ResearchDataReader {
    /** users: only user_id, role, is_verified, created_at — NO email/name/hash. */
    List<Map<String, Object>> readUsersPseudonymized();

    List<Map<String, Object>> readLearningEvents();
    List<Map<String, Object>> readFeedbackLogs();
    List<Map<String, Object>> readHintUsage();
    List<Map<String, Object>> readTaskChangeAnalyses();
    List<Map<String, Object>> readProblemStatementRatings();
    List<Map<String, Object>> readTasks();
    List<Map<String, Object>> readTieredTaskSequences();
    /** worksheets_sequence junction table (worksheet_id, sequence_id, last_used_at). */
    List<Map<String, Object>> readWorksheetsSequence();
    List<Map<String, Object>> readConcepts();
    List<Map<String, Object>> readSequenceConceptJunction();
    List<Map<String, Object>> readWorksheets();
    List<Map<String, Object>> readClasses();
    List<Map<String, Object>> readClassStudents();
    List<Map<String, Object>> readClassWorksheets();
    List<Map<String, Object>> readSequenceTags();
    List<Map<String, Object>> readCurriculumTopics();
    List<Map<String, Object>> readCurriculumCodeExamples();
    List<Map<String, Object>> readConceptCurriculumTopicJunction();
}
