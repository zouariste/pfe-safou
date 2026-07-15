package de.uni_passau.fim.se2.domain.interfaces;

import de.uni_passau.fim.se2.domain.entities.HintUsageDTO;
import java.util.List;
import java.util.UUID;

public interface HintUsageRepository {
    void save(HintUsageDTO usage);
    List<HintUsageDTO> findByTaskId(UUID taskId);
}
