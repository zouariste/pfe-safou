package com.cisternino.domain.entities;

import lombok.*;

import java.util.*;

@Data @AllArgsConstructor
public class SnippetDTO {
    private String name;
    private String language;
    private String code;
    private List<String> concepts; // Ground Truth
}