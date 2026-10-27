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
package de.uni_passau.fim.se2.application.service;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class CodeSanitizerService {

    /**
     * Strips comments from code, using the comment syntax of the given language. Code in a language without known
     * comment syntax is returned unchanged.
     *
     * @param code     the code to clean, or null
     * @param language the programming language of the code
     * @return the code without comments, trimmed; empty for null input
     */
    public String removeComments(String code, String language) {
        if (code == null) {
            return "";
        }

        String regex = regexByLanguage(language);

        if (regex.isEmpty()) {
            return code;
        }

        return Pattern.compile(regex, Pattern.MULTILINE)
            .matcher(code)
            .replaceAll("")
            .trim();
    }

    private String regexByLanguage(String language) {
        String regex;
        switch (language.toLowerCase(Locale.ROOT)) {
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

    /**
     * Checks whether code contains comments in the syntax of the given language.
     *
     * @param code     the code to check, or null
     * @param language the programming language of the code
     * @return true if a comment was found
     */
    public boolean hasComments(String code, String language) {
        if (code == null) {
            return false;
        }

        String regex = regexByLanguage(language);
        return !regex.isEmpty() && Pattern.compile(regex, Pattern.MULTILINE).matcher(code).find();
    }
}
