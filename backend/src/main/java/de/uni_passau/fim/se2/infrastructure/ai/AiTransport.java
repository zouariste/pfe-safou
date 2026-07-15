package de.uni_passau.fim.se2.infrastructure.ai;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public interface AiTransport {
    /**
     * Sends a JSON-formatted GPT request to the OpenAI API.
     *
     * <p>This method uses the configured HTTP client to send the request and
     * returns the full HTTP response body.
     *
     * @param request GPT request body as JSON
     * @return the HTTP response from OpenAI
     * @throws IOException if the request fails
     * @throws InterruptedException if the sending is interrupted
     */
    HttpResponse<String> sendRequest(HttpRequest request)
            throws IOException, InterruptedException;
}