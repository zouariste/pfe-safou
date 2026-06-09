package com.cisternino.infrastructure.ai;

import com.cisternino.domain.entities.*;

// application/ai/Selection.java
public record Selection(AiProviderKey provider, OpenAiModel openAiModelOrNull) {}