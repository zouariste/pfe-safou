package com.cisternino.infrastructure.ai.openai;

import com.cisternino.application.service.*;
import com.cisternino.domain.model.ConceptCatalog;
import com.cisternino.infrastructure.ai.AiProps;
import com.cisternino.infrastructure.ai.AiTransport;
import com.cisternino.infrastructure.ai.HttpClientTransport;
import com.cisternino.infrastructure.ai.PromptBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(AiProps.class)
public class OpenAiClientConfig {

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    public AiTransport gptTransport(HttpClient httpClient) {
        return new HttpClientTransport(httpClient);
    }

    @Bean
    public OpenAiClient chatGPTClient(
            @Value("${openai.api.key}") String apiKey,
            AiTransport transport,
            PromptBuilder promptBuilder,
            AiProps aiProps,
            ConceptCatalog conceptCatalog,
            CodeSanitizerService sanitizer
    ) {
        return new OpenAiClient(apiKey, transport, promptBuilder, aiProps, conceptCatalog, sanitizer);
    }
}
