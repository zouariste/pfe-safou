package com.cisternino.domain.interfaces;

import com.cisternino.domain.entities.VersionDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing versions.
 * Provides methods to save, retrieve, and delete instances
 * of VersionDTO.
 */
public interface VersionRepository {

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
    void save(VersionDTO version);

    /**
     * Retrieves all version data transfer objects (VersionDTO) stored in the repository.
     * Returns a list of VersionDTO instances representing the available versions,
     * including details such as their unique identifiers, version names, creation dates,
     * and descriptions.
     *
     * @return a list of VersionDTO objects representing all versions in the repository,
     *         or an empty list if no versions are found
     */
    List<VersionDTO> findAll();

    /**
     * Retrieves a specific version from the repository based on the
     * details provided in the given VersionDTO instance.
     *
     * @param version the VersionDTO object representing the version to be retrieved.
     *                It should include the necessary details, such as the unique identifier (id),
     *                to locate the specific version in the repository.
     */
    void findById(VersionDTO version);

    /**
     * Deletes the specified version from the repository.
     * This method removes the VersionDTO instance and its associated data.
     *
     * @param version the VersionDTO object representing the version to be deleted,
     *                including its unique identifier and other related details
     */
    void delete(VersionDTO version);
}