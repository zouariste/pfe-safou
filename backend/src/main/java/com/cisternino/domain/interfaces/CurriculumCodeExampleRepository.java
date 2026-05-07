package com.cisternino.domain.interfaces;

import com.cisternino.domain.entities.CurriculumCodeExampleDTO;
import java.util.List;
import java.util.UUID;

public interface CurriculumCodeExampleRepository {
    List<CurriculumCodeExampleDTO> findByTopicId(UUID topicId);
    List<CurriculumCodeExampleDTO> findByTopicIdAndLanguage(UUID topicId, String language);
}
