package com.cisternino.infrastructure.ai;

import com.cisternino.domain.interfaces.*;
import com.cisternino.infrastructure.ai.claude.*;
import com.cisternino.infrastructure.ai.gemini.*;
import com.cisternino.infrastructure.ai.openai.*;
import org.springframework.boot.context.properties.*;
import org.springframework.context.annotation.*;

// infrastructure/ai/AiClientConfig.java
@Configuration
@EnableConfigurationProperties(AiProps.class)
public class AiClientConfig {

    @Bean
    @Primary
    public AiModelClient aiModelClient(
            AiProps props,
            OpenAiClient openai,
            ClaudeClient claude,
            GeminiClient gemini
    ) {
        return switch (props.getDefaultProvider()) {
            case OPENAI -> openai;
            case CLAUDE -> claude;
            case GEMINI -> gemini;
        };
    }
}