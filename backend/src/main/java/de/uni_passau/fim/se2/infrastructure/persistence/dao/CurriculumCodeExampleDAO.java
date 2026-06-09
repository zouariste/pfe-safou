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
package de.uni_passau.fim.se2.infrastructure.persistence.dao;

import com.cisternino.domain.entities.*;
import com.cisternino.domain.interfaces.*;
import com.cisternino.infrastructure.persistence.mapper.*;
import de.uni_passau.fim.se2.domain.entities.CurriculumCodeExampleDTO;
import de.uni_passau.fim.se2.domain.interfaces.CurriculumCodeExampleRepository;
import de.uni_passau.fim.se2.infrastructure.persistence.mapper.CurriculumCodeExampleRowMapper;
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