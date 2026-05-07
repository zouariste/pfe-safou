package com.cisternino.infrastructure.persistence.dao;

import com.cisternino.domain.entities.*;
import com.cisternino.domain.interfaces.*;

import java.util.*;

import com.cisternino.infrastructure.persistence.mapper.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgreVersionDAO implements VersionRepository {

    private final JdbcTemplate jdbc;

    public PostgreVersionDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Persists the given version details into the repository.
     * This method saves the provided VersionDTO object, ensuring
     * that the version information, such as ID, name, creation timestamp,
     * and description, is stored for future retrieval or modification.
     *
     * @param version the VersionDTO object representing the version to be saved.
     *                It includes details such as a unique identifier, version name,
     *                creation date, and description.
     */
    @Override
    public void save(VersionDTO version) {
        String sql = """
                         INSERT INTO versions (version_name, created_at, description)
                         VALUES (?, ?, ?)
                         RETURNING id
                     """;

        UUID id = jdbc.queryForObject(sql, UUID.class,
                                      version.getVersionName(),
                                      version.getCreatedAt(),
                                      version.getDescription()
        );

        version.setId(id);
    }

    /**
     * Retrieves all version data transfer objects (VersionDTO) stored in the repository.
     * Returns a list of VersionDTO instances representing the available versions,
     * including details such as their unique identifiers, version names, creation dates,
     * and descriptions.
     *
     * @return a list of VersionDTO objects representing all versions in the repository,
     * or an empty list if no versions are found
     */
    @Override
    public List<VersionDTO> findAll() {
        String sql = "SELECT id, version_name, created_at, description FROM versions";
        return jdbc.query(sql, new VersionRowMapper());
    }

    /**
     * Retrieves a specific version from the repository based on the
     * details provided in the given VersionDTO instance.
     *
     * @param version the VersionDTO object representing the version to be retrieved.
     *                It should include the necessary details, such as the unique identifier (id),
     *                to locate the specific version in the repository.
     */
    @Override
    public void findById(VersionDTO version) {
        String sql = """
                         SELECT version_name, created_at, description 
                         FROM versions 
                         WHERE id = ?
                     """;

        jdbc.queryForObject(sql, (rs, rowNum) -> {
            version.setVersionName(rs.getString("version_name"));
            version.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            version.setDescription(rs.getString("description"));
            return version;
        }, version.getId());
    }

    /**
     * Deletes the specified version from the repository.
     * This method removes the VersionDTO instance and its associated data.
     *
     * @param version the VersionDTO object representing the version to be deleted,
     *                including its unique identifier and other related details
     */
    @Override
    public void delete(VersionDTO version) {
        String sql = "DELETE FROM versions WHERE id = ?";
        jdbc.update(sql, version.getId());
    }
}