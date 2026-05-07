package com.cisternino.infrastructure.ai;

import com.cisternino.domain.entities.*;
import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "ai")
public class AiProps {
    private AiProviderKey defaultProvider = AiProviderKey.OPENAI;
    private OpenAi openai = new OpenAi();

    @Setter
    @Getter
    public static class OpenAi {
        private OpenAiModel defaultModel = OpenAiModel.GPT_5;

    }
}