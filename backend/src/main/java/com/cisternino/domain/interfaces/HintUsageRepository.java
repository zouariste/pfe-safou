package com.cisternino.domain.interfaces;

import com.cisternino.domain.entities.HintUsageDTO;
import java.util.List;
import java.util.UUID;

public interface HintUsageRepository {
    void save(HintUsageDTO usage);
    List<HintUsageDTO> findByTaskId(UUID taskId);
}
