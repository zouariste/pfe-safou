package de.uni_passau.fim.se2.domain.interfaces;

import de.uni_passau.fim.se2.domain.entities.CurriculumCodeExampleDTO;
import java.util.List;
import java.util.UUID;

public interface CurriculumCodeExampleRepository {
    List<CurriculumCodeExampleDTO> findByTopicId(UUID topicId);
    List<CurriculumCodeExampleDTO> findByTopicIdAndLanguage(UUID topicId, String language);

    /** AP32 admin: load a single example by id, or {@code null} if none. */
    CurriculumCodeExampleDTO findById(UUID id);
    /** AP32 admin: insert a new example; returns the generated id. */
    UUID insert(CurriculumCodeExampleDTO example);
    /** AP32 admin: update an existing example (by {@code example.id}). */
    void update(CurriculumCodeExampleDTO example);
    /** AP32 admin: delete an example by id. */
    void deleteById(UUID id);
}
