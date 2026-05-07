package com.cisternino.infrastructure.persistence.dao;

import com.cisternino.domain.entities.CurriculumTopicDTO;
import com.cisternino.domain.interfaces.CurriculumTopicRepository;
import com.cisternino.infrastructure.persistence.mapper.CurriculumTopicRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgreCurriculumTopicDAO implements CurriculumTopicRepository {

    private static final CurriculumTopicRowMapper ROW_MAPPER = new CurriculumTopicRowMapper();
    private final JdbcTemplate jdbc;

    public PostgreCurriculumTopicDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(CurriculumTopicDTO topic) {
        jdbc.update("""
            INSERT INTO curriculum_topics (id, name, description, grade_level, track, learning_area, parent_id, sort_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description,
                grade_level = EXCLUDED.grade_level, track = EXCLUDED.track,
                learning_area = EXCLUDED.learning_area, parent_id = EXCLUDED.parent_id,
                sort_order = EXCLUDED.sort_order
            """,
            topic.getId(), topic.getName(), topic.getDescription(),
            topic.getGradeLevel(), topic.getTrack(), topic.getLearningArea(),
            topic.getParentId(), topic.getSortOrder());
    }

    @Override
    public List<CurriculumTopicDTO> findAll() {
        return jdbc.query(
            "SELECT id, name, description, grade_level, track, learning_area, parent_id, sort_order FROM curriculum_topics ORDER BY sort_order, name",
            ROW_MAPPER);
    }

    @Override
    public Optional<CurriculumTopicDTO> findById(UUID id) {
        var results = jdbc.query(
            "SELECT id, name, description, grade_level, track, learning_area, parent_id, sort_order FROM curriculum_topics WHERE id = ?",
            ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<CurriculumTopicDTO> findTopLevel() {
        return jdbc.query(
            "SELECT id, name, description, grade_level, track, learning_area, parent_id, sort_order FROM curriculum_topics WHERE parent_id IS NULL ORDER BY sort_order, name",
            ROW_MAPPER);
    }

    @Override
    public List<CurriculumTopicDTO> findByParentId(UUID parentId) {
        return jdbc.query(
            "SELECT id, name, description, grade_level, track, learning_area, parent_id, sort_order FROM curriculum_topics WHERE parent_id = ? ORDER BY sort_order, name",
            ROW_MAPPER, parentId);
    }

    @Override
    public void delete(CurriculumTopicDTO topic) {
        jdbc.update("DELETE FROM curriculum_topics WHERE id = ?", topic.getId());
    }
}
