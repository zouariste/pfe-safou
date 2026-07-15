package de.uni_passau.fim.se2.infrastructure.ai;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


public class HttpClientTransport implements AiTransport {

    /**
     * HttpClient used to send and receive requests.
     */
    private final HttpClient httpClient;

    /**
     * Constructs a new instance of the HttpClientTransport.
     * @param client the HttpClient used to send and receive requests.
     */
    public HttpClientTransport(final HttpClient client) {
        this.httpClient = client;
    }

    /**
     * Sends a request using the HttpClient.
     * @param request GPT request body.
     * @return the HttpResponse.
     * @throws IOException throws IOException
     * @throws InterruptedException throws InterruptedException
     */
    @Override
    public HttpResponse<String> sendRequest(final HttpRequest request)
            throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}