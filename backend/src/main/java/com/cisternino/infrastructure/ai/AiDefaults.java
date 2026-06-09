package com.cisternino.infrastructure.ai;

import com.cisternino.domain.entities.*;

// application/ai/AiDefaults.java
public interface AiDefaults {
    AiProviderKey defaultProvider();
    OpenAiModel defaultOpenAiModel();
}