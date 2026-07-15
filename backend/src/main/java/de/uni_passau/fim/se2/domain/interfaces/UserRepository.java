package de.uni_passau.fim.se2.domain.interfaces;

import de.uni_passau.fim.se2.domain.entities.UserDTO;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    UserDTO save(UserDTO user);
    Optional<UserDTO> findById(UUID userId);
    Optional<UserDTO> findByEmail(String email);
    List<UserDTO> findAll();
    void update(UserDTO user);
    void deleteById(UUID userId);
    boolean existsByEmail(String email);

    void setMustChangePassword(UUID userId, boolean mustChangePassword);

    // Paginated search for admin
    List<UserDTO> findAllPaginated(String search, String sortColumn, String sortDir, int offset, int limit);
    long countAll(String search);
}
