/*
 * Copyright (C) 2026 CodeOwl contributors
 *
 * This file is part of CodeOwl.
 *
 * CodeOwl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * CodeOwl is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CodeOwl. If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_passau.fim.se2.infrastructure.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import de.uni_passau.fim.se2.domain.interfaces.AiModelClient;
import de.uni_passau.fim.se2.infrastructure.ai.claude.ClaudeClient;
import de.uni_passau.fim.se2.infrastructure.ai.gemini.GeminiClient;
import de.uni_passau.fim.se2.infrastructure.ai.openai.OpenAiClient;

// infrastructure/ai/AiClientConfig.java
@Configuration
@EnableConfigurationProperties(AiProps.class)
public class AiClientConfig {

    /**
     * Selects the AI client for {@code ai.defaultProvider}.
     *
     * @param props  the AI configuration
     * @param openai the OpenAI client
     * @param claude the Claude client
     * @param gemini the Gemini client
     * @return the client for the configured provider
     */
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
