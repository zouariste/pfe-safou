package com.cisternino.infrastructure.persistence.dao;

import com.cisternino.domain.entities.*;
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
}