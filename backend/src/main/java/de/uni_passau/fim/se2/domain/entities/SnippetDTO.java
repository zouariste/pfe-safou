package de.uni_passau.fim.se2.domain.entities;

import lombok.*;

import java.util.*;

@Data @AllArgsConstructor
public class SnippetDTO {
    private String name;
    private String language;
    private String code;
    private List<String> concepts; // Ground Truth
}