package de.uni_passau.fim.se2.domain.exceptions;

public class GenerationException extends RuntimeException { // or Exception
    public GenerationException(String message) { super(message); }
    public GenerationException(String message, Throwable cause) { super(message, cause); }
}