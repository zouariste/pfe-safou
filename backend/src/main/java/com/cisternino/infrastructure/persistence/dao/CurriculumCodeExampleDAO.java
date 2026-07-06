package com.cisternino.infrastructure.persistence.dao;

import com.cisternino.domain.entities.*;
import com.cisternino.domain.exceptions.*;
import com.cisternino.domain.interfaces.*;
import com.cisternino.infrastructure.persistence.mapper.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.*;

import java.util.*;

@Repository
public class CurriculumCodeExampleDAO implements CurriculumCodeExampleRepository {

    private static final CurriculumCodeExampleRowMapper ROW_MAPPER = new CurriculumCodeExampleRowMapper();
    private final JdbcTemplate jdbc;

    public CurriculumCodeExampleDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CurriculumCodeExampleDTO> findByTopicId(UUID topicId) {
        return jdbc.query(
                "SELECT id, topic_id, language, title, code, problem_statement, difficulty, notes, created_at FROM curriculum_code_examples WHERE topic_id = ? ORDER BY difficulty, title",
                ROW_MAPPER, topicId);
    }

    @Override
    public List<CurriculumCodeExampleDTO> findByTopicIdAndLanguage(UUID topicId, String language) {
        return jdbc.query(
                "SELECT id, topic_id, language, title, code, problem_statement, difficulty, notes, created_at FROM curriculum_code_examples WHERE topic_id = ? AND language = ? ORDER BY difficulty, title",
                ROW_MAPPER, topicId, language);
    }

    @Override
    public CurriculumCodeExampleDTO findById(UUID id) {
        return jdbc.query(
                "SELECT id, topic_id, language, title, code, problem_statement, difficulty, notes, created_at FROM curriculum_code_examples WHERE id = ?",
                ROW_MAPPER, id).stream().findFirst().orElse(null);
    }

    @Override
    public UUID insert(CurriculumCodeExampleDTO ex) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO curriculum_code_examples (id, topic_id, language, title, code, problem_statement, difficulty, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, ex.getTopicId(), ex.getLanguage(), ex.getTitle(), ex.getCode(),
                ex.getProblemStatement(), ex.getDifficulty(), ex.getNotes());
        return id;
    }

    @Override
    public void update(CurriculumCodeExampleDTO ex) {
        int updated = jdbc.update(
                "UPDATE curriculum_code_examples SET language = ?, title = ?, code = ?, problem_statement = ?, difficulty = ?, notes = ? WHERE id = ?",
                ex.getLanguage(), ex.getTitle(), ex.getCode(), ex.getProblemStatement(),
                ex.getDifficulty(), ex.getNotes(), ex.getId());
        if (updated != 1) {
            throw new DomainValidationException("Curriculum example not found: " + ex.getId());
        }
    }

    @Override
    public void deleteById(UUID id) {
        jdbc.update("DELETE FROM curriculum_code_examples WHERE id = ?", id);
    }
}