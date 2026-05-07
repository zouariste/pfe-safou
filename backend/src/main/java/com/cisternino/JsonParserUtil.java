package com.cisternino;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * Utility class for parsing JSON strings into {@link JsonNode} objects.
 * <p>
 * This class follows the Singleton pattern to ensure a single instance of the JSON parser
 * is used throughout the application.
 * </p>
 *
 * <p>
 * It uses the Jackson {@link ObjectMapper} for parsing JSON.
 * </p>
 *
 * @author Luca Cisternino
 * @version 1.0
 */
public class JsonParserUtil {
    
    /**
     * The single instance of {@code JsonParserUtil}, ensuring only one instance
     * exists throughout the application's lifecycle.
     */
    private static final JsonParserUtil INSTANCE = new JsonParserUtil();

    /**
     * Jackson's {@link ObjectMapper} instance used for parsing JSON.
     */
    private final ObjectMapper mapper;

    /**
     * Private constructor to prevent external instantiation.
     * Initializes the {@link ObjectMapper} instance.
     */
    private JsonParserUtil() {
        mapper = new ObjectMapper();
    }

    /**
     * Retrieves the singleton instance of {@code JsonParserUtil}.
     *
     * @return the single instance of {@code JsonParserUtil}
     */
    public static JsonParserUtil getInstance() {
        return INSTANCE;
    }

    /**
     * Parses a JSON string and returns a {@link JsonNode} representation.
     *
     * @param json the JSON string to parse
     * @return the parsed {@link JsonNode} object
     * @throws IOException if an error occurs while parsing the JSON string
     */
    public JsonNode parseJson(String json) throws IOException {
        return mapper.readTree(json);
    }
}
