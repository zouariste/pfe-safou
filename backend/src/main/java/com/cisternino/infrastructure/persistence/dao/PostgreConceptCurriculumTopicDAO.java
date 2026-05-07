package com.cisternino.infrastructure.persistence.dao;

import com.cisternino.domain.entities.*;
import com.cisternino.domain.interfaces.ConceptCurriculumTopicRepository;
import com.cisternino.infrastructure.persistence.mapper.ConceptCurriculumTopicRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class PostgreConceptCurriculumTopicDAO implements ConceptCurriculumTopicRepository {

    private final JdbcTemplate jdbc;

    public PostgreConceptCurriculumTopicDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(ConceptCurriculumTopicDTO dto) {
        jdbc.update(
            "INSERT INTO concept_curriculum_topic (concept_id, topic_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            dto.getConceptId(), dto.getTopicId());
    }

    @Override
    public List<CurriculumTopicDTO> findAllByConcept(ConceptDTO concept) {
        return jdbc.query("""
            SELECT ct.id, ct.name, ct.description, ct.grade_level, ct.track, ct.learning_area, ct.parent_id, ct.sort_order
            FROM curriculum_topics ct
            JOIN concept_curriculum_topic cct ON ct.id = cct.topic_id
            WHERE cct.concept_id = ?
            ORDER BY ct.sort_order, ct.name
            """,
            (rs, rowNum) -> {
                CurriculumTopicDTO dto = new CurriculumTopicDTO();
                dto.setId(UUID.fromString(rs.getString("id")));
                dto.setName(rs.getString("name"));
                dto.setDescription(rs.getString("description"));
                int grade = rs.getInt("grade_level");
                dto.setGradeLevel(rs.wasNull() ? null : grade);
                dto.setTrack(rs.getString("track"));
                dto.setLearningArea(rs.getString("learning_area"));
                String parentStr = rs.getString("parent_id");
                dto.setParentId(parentStr != null ? UUID.fromString(parentStr) : null);
                dto.setSortOrder(rs.getInt("sort_order"));
                return dto;
            },
            concept.getId());
    }

    @Override
    public List<ConceptDTO> findAllByCurriculumTopic(CurriculumTopicDTO topic) {
        return jdbc.query("""
            SELECT c.id, c.name, c.description, c.generated
            FROM concepts c
            JOIN concept_curriculum_topic cct ON c.id = cct.concept_id
            WHERE cct.topic_id = ?
            ORDER BY c.name
            """,
            (rs, rowNum) -> {
                ConceptDTO dto = new ConceptDTO();
                dto.setId(UUID.fromString(rs.getString("id")));
                dto.setName(rs.getString("name"));
                dto.setDescription(rs.getString("description"));
                dto.setGenerated(rs.getBoolean("generated"));
                return dto;
            },
            topic.getId());
    }

    @Override
    public void delete(ConceptCurriculumTopicDTO dto) {
        jdbc.update("DELETE FROM concept_curriculum_topic WHERE concept_id = ? AND topic_id = ?",
            dto.getConceptId(), dto.getTopicId());
    }
}
