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

import org.springframework.boot.context.properties.ConfigurationProperties;

import de.uni_passau.fim.se2.domain.entities.AiProviderKey;
import de.uni_passau.fim.se2.domain.entities.OpenAiModel;
import lombok.Getter;
import lombok.Setter;

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

        /**
         * Base URL of an OpenAI-compatible API, without the {@code /chat/completions} suffix. Point it at a LiteLLM or
         * vLLM server to use a self-hosted model.
         */
        private String baseUrl = "https://api.openai.com/v1";

        /**
         * Model name sent instead of the one mapped from {@link OpenAiModel}, for servers whose models are not OpenAI's
         * (e.g. {@code qwen38-27b}). Blank means no override.
         */
        private String modelOverride = "";

        /**
         * {@code reasoning_effort} sent with every request, e.g. {@code none} to stop a self-hosted model from thinking
         * before it answers, or {@code low}/{@code medium}/{@code high}. Blank omits the field (the server's default).
         */
        private String reasoningEffort = "";

    }
}
