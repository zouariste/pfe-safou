package de.uni_passau.fim.se2.application.service;

import org.springframework.stereotype.*;

import java.util.regex.Pattern;

@Service
public class CodeSanitizerService {

    public String removeComments(String code, String language) {
        if (code == null) return "";

        String regex = regexByLanguage(language);

        if (regex.isEmpty()) return code;

        return Pattern.compile(regex, Pattern.MULTILINE)
                .matcher(code)
                .replaceAll("")
                .trim();
    }

    private String regexByLanguage(String language) {
        String regex;
        switch (language.toLowerCase()) {
            case "java", "c", "cpp", "c#", "javascript" ->
                    regex = "(?s)//.*?$|/\\*.*?\\*/";
            case "python" ->
                    regex = "(?m)#.*$";
            case "sql" ->
                    regex = "(?s)--.*?$|/\\*.*?\\*/";
            case "html" ->
                    regex = "(?s)<!--.*?-->";
            default ->
                    regex = "";
        }
        return regex;
    }

    public boolean hasComments(String code, String language) {
        if (code == null) return false;

        String regex = regexByLanguage(language);
        return !regex.isEmpty() && Pattern.compile(regex, Pattern.MULTILINE).matcher(code).find();
    }
}