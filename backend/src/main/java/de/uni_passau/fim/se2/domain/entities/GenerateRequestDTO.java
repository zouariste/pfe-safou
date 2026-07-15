package de.uni_passau.fim.se2.domain.entities;

import lombok.*;

import java.util.*;

@Setter
@Getter
public class GenerateRequestDTO {
    private String taskDescription;
    private List<String> concepts;
    private String language;
    private String codeSnippet;

}