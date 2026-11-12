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
package de.uni_passau.fim.se2.infrastructure.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.uni_passau.fim.se2.application.service.CodeSanitizerService;
import de.uni_passau.fim.se2.infrastructure.ai.AiProps;
import de.uni_passau.fim.se2.infrastructure.ai.AiTransport;
import de.uni_passau.fim.se2.infrastructure.ai.IdentityLoader;
import de.uni_passau.fim.se2.infrastructure.ai.PromptBuilder;

/**
 * The client can talk to any OpenAI-compatible API, such as a LiteLLM proxy serving self-hosted models.
 */
@DisplayName("OpenAI-compatible endpoint and model configuration")
class OpenAiClientEndpointTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PromptBuilder promptBuilder = new PromptBuilder(MAPPER, new IdentityLoader("classpath:unused/"));

    @Test
    @DisplayName("the endpoint is built from the base URL, with or without a trailing slash")
    void endpointFromBaseUrl() {
        assertEquals(
            URI.create("https://api.openai.com/v1/chat/completions"),
            OpenAiClient.chatCompletionsUri("https://api.openai.com/v1")
        );
        assertEquals(
            URI.create("https://llm.example.org/v1/chat/completions"),
            OpenAiClient.chatCompletionsUri(" https://llm.example.org/v1/ ")
        );
        assertEquals(
            URI.create("https://api.openai.com/v1/chat/completions"),
            OpenAiClient.chatCompletionsUri("")
        );
    }

    @Test
    @DisplayName("by default requests go to OpenAI with the mapped GPT model")
    void defaults() throws Exception {
        HttpRequest request = sentRequest(new AiProps());

        assertEquals(URI.create("https://api.openai.com/v1/chat/completions"), request.uri());
        assertEquals("gpt-5", bodyOf(request).path("model").asText());
        assertTrue(bodyOf(request).path("reasoning_effort").isMissingNode());
    }

    @Test
    @DisplayName("a configured reasoning effort is sent with the request")
    void reasoningEffort() throws Exception {
        AiProps props = new AiProps();
        props.getOpenai().setReasoningEffort("none");

        assertEquals("none", bodyOf(sentRequest(props)).path("reasoning_effort").asText());
    }

    @Test
    @DisplayName("a configured base URL and model override replace OpenAI's")
    void override() throws Exception {
        AiProps props = new AiProps();
        props.getOpenai().setBaseUrl("https://litellm.example.org/v1");
        props.getOpenai().setModelOverride("qwen38-27b");

        HttpRequest request = sentRequest(props);

        assertEquals(URI.create("https://litellm.example.org/v1/chat/completions"), request.uri());
        assertEquals("qwen38-27b", bodyOf(request).path("model").asText());
    }

    @Test
    @DisplayName("a JSON request is held to the caller's schema, not the task-description one")
    @SuppressWarnings("unchecked")
    void jsonSchemaRequest() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        ObjectNode body = MAPPER.createObjectNode();
        body.putArray("choices").addObject().putObject("message").put("content", "{\"contextLines\":[1]}");
        when(response.body()).thenReturn(body.toString());
        AiTransport transport = mock(AiTransport.class);
        when(transport.sendRequest(any())).thenReturn(response);
        ObjectNode schema = MAPPER.createObjectNode().put("type", "object");

        String answer = new OpenAiClient(
            "test-key", transport, promptBuilder, new AiProps(), null, mock(CodeSanitizerService.class)
        ).generateJson("system", "user", "context_lines", schema);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(transport).sendRequest(captor.capture());
        var format = bodyOf(captor.getValue()).path("response_format");
        assertEquals("json_schema", format.path("type").asText());
        assertEquals("context_lines", format.path("json_schema").path("name").asText());
        assertTrue(format.path("json_schema").path("strict").asBoolean());
        assertEquals(schema, format.path("json_schema").path("schema"));
        assertEquals("{\"contextLines\":[1]}", answer);
    }

    @SuppressWarnings("unchecked")
    private HttpRequest sentRequest(AiProps props) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        ObjectNode body = MAPPER.createObjectNode();
        body.putArray("choices").addObject().putObject("message").put("content", "{\"code\":\"print(1)\"}");
        when(response.body()).thenReturn(body.toString());

        AiTransport transport = mock(AiTransport.class);
        when(transport.sendRequest(any())).thenReturn(response);

        new OpenAiClient("test-key", transport, promptBuilder, props, null, mock(CodeSanitizerService.class))
            .solveProblemSimple("Print one", "python");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(transport).sendRequest(captor.capture());
        return captor.getValue();
    }

    private static com.fasterxml.jackson.databind.JsonNode bodyOf(HttpRequest request) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompletableFuture<Void> done = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                out.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                done.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        done.get();
        return MAPPER.readTree(out.toString(StandardCharsets.UTF_8));
    }
}
